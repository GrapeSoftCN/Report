package interfaceApplication;

import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import Concurrency.distributedLocker;
import JGrapeSystem.rMsg;
import apps.appIns;
import apps.appsProxy;
import check.checkHelper;
import interfaceModel.GrapeDBSpecField;
import interfaceModel.GrapeTreeDBModel;
import nlogger.nlogger;
import sms.ruoyaMASDB;
import string.StringHelper;
import time.TimeHelper;

/**
 * 设置举报件接收短信的管理员信息
 * 
 *
 */
public class ReportTask {
	private String pkString;
	private GrapeTreeDBModel db;
	private static final String lockerName = "reportTask_Query_Locker";
	private static HashMap<Integer, ScheduledExecutorService> ticktockThread = null;

	static {
		ticktockThread = new HashMap<>();
	}

	public ReportTask() {
		db = new GrapeTreeDBModel();
		GrapeDBSpecField gdb = new GrapeDBSpecField();
		gdb.importDescription(appsProxy.tableConfig("reportTask"));
		db.descriptionModel(gdb).bind();
		pkString = db.getPk();
	}

	/**
	 * 获得当前模块任务状态
	 * 
	 * @return
	 */
	private boolean queryService() {
		distributedLocker sLocker = new distributedLocker(lockerName);
		return sLocker.isExisting();
	}

	/**
	 * 开启当前模块任务状态
	 * 
	 * @return
	 */
	public String startService() {
		appIns apps = appsProxy.getCurrentAppInfo();
		if (apps != null && !ticktockThread.containsKey(apps.appid)) {
			ScheduledExecutorService serv = Executors.newSingleThreadScheduledExecutor();
			;
			distributedLocker servLocker = distributedLocker.newLocker(lockerName);
			if (!servLocker.lock()) {// 服务 本来没有锁
				serv.scheduleAtFixedRate(() -> {
					distributedLocker sLocker = new distributedLocker(lockerName);

					distributedLocker sLockerReport = new distributedLocker(lockerName);
					if (sLockerReport.lock()) {
						// 需要复制环境
						appsProxy.proxyCall("/GrapeReport/ReportTask/ExecuteTask", apps);

						sLockerReport.releaseLocker();
					}
					if (!sLocker.isExisting()) {
						ReportTask task = new ReportTask();
						task.stopService();
					}
				}, 0, 1, TimeUnit.SECONDS);
			}
		}
		return rMsg.netState(true);
	}

	/**
	 * 停止当前模块任务状态
	 * 
	 * @return
	 */
	public String stopService() {
		String result = rMsg.netMSG(false, "当前模块任务已为停止状态，请先启动模块任务");
		// 获取当前任务状态
		if (queryService()) {
			appIns apps = appsProxy.getCurrentAppInfo();
			if (ticktockThread.containsKey(apps.appid)) {
				ScheduledExecutorService serv = ticktockThread.get(apps.appid);
				if (!serv.isTerminated()) {
					serv.shutdown();
				}
				ticktockThread.remove(apps.appid);
			}
			(new distributedLocker(lockerName)).releaseLocker();
			result = rMsg.netState(true);
		}
		return result;
	}

	/**
	 * 执行任务，即发送短信到管理员
	 * 
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public void ExecuteTask() {
		String phoneNo = "", id;
		JSONArray array = db.eq("state", 1).scan((rArray) -> {
			JSONObject obj;
			String phone;
			long timediff = 0, neartime = 0;
			JSONArray _aArray = new JSONArray();
			if (rArray != null && rArray.size() > 0) {
				for (Object object : rArray) {
					obj = (JSONObject) object;
					timediff = obj.getLong("timediff"); // 发送短信间隔时间
					neartime = obj.getLong("neartime"); // 最后一次发送短信时间
					phone = obj.getString("phone"); // 接收短信手机号
					if (checkHelper.checkMobileNumber(phone) && timediff + neartime <= TimeHelper.nowMillis()) {
						_aArray.add(obj);
					}
				}
			}
			return _aArray;
		}, 50);
		JSONObject obj;
		long reportCount = 0,timediff =0;
		for (Object object : array) {
			obj = (JSONObject) object;
			id = obj.getMongoID(pkString);
			phoneNo = obj.getString("phone");
			reportCount = new Report().getReportCount(timediff);
			if (checkHelper.checkMobileNumber(phoneNo) && reportCount > 0) {
				ruoyaMASDB.sendSMS(phoneNo, "新增举报量为：" + reportCount + "，请及时处理");
			}
			db.eq(pkString, id).data(new JSONObject("neartime", TimeHelper.nowMillis())).update();
		}
		nlogger.logout("任务已执行！");
	}

//	/**
//	 * 获取符合条件的任务信息 查询条件 1.手机号正确 2.接收短信状态 state:1 3.间隔时间+上一次执行时间<=当前时间
//	 * 
//	 * @return
//	 */
//	@SuppressWarnings("unchecked")
//	private JSONArray getTasks() {
//		JSONArray _aArray = new JSONArray();
//		JSONObject obj;
//		long timediff = 0, neartime = 0;
//		String phoneNo = "";
//		JSONArray array = db.eq("state", 1).limit(30).select();
//		if (array != null && array.size() > 0) {
//			for (Object object : array) {
//				obj = (JSONObject) object;
//				timediff = obj.getLong("timediff"); // 发送短信间隔时间
//				neartime = obj.getLong("neartime"); // 最后一次发送短信时间
//				phoneNo = obj.getString("phone"); // 接收短信手机号
//				if (checkHelper.checkMobileNumber(phoneNo) && timediff + neartime <= TimeHelper.nowMillis()) {
//					_aArray.add(obj);
//				}
//			}
//		}
//		return _aArray;
//	}

	/**
	 * 新增管理员 手机号与时间间隔相同，则不允许重复添加
	 * 
	 * @param info
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public String insert(String info) {
		Object obj = null;
		JSONObject object = JSONObject.toJSON(info);
		if (object == null || object.size() <= 0) {
			return rMsg.netMSG(2, "非法参数");
		}
		if (checkParam(object)) {
			return rMsg.netMSG(3, "该管理员信息已存在");
		}
		object.put("time", TimeHelper.nowMillis());
		object.put("state", 0); // 不接收短信

		obj = db.data(object).autoComplete().insertOnce();
		return rMsg.netMSG(true, (String) obj);
	}

	/**
	 * 验证信息在库中是否已存在
	 * 
	 * @param object
	 * @return
	 */
	private boolean checkParam(JSONObject object) {
		String phone = "";
		long timediff = 0;
		JSONObject obj = null;
		if (object != null && object.size() > 0) {
			if (object.containsKey("phone")) {
				phone = object.getString("phone");
			}
			if (object.containsKey("timediff")) {
				timediff = object.getLong("timediff");
			}
			obj = db.eq("phone", phone).eq("timediff", timediff).find();
		}
		return obj != null && obj.size() > 0;
	}

	/**
	 * 修改管理员信息
	 * 
	 * @param info
	 * @return
	 */
	public String update(String id, String info) {
		if (!StringHelper.InvaildString(id)) {
			return rMsg.netMSG(false, "无效id");
		}
		JSONObject object = JSONObject.toJSON(info);
		if (object == null || object.size() <= 0) {
			return rMsg.netMSG(false, "非法参数");
		}
		if (checkParam(object)) {
			return rMsg.netMSG(3, "该管理员信息已存在");
		}
		db.enableCheck();// 开启权限检查
		object = db.eq(pkString, id).data(object).update();
		return rMsg.netState(object != null);
	}

	/**
	 * 删除管理员信息
	 * 
	 * @param info
	 * @return
	 */
	public String delete(String ids) {
		long rl = 0;
		boolean rb = true;
		String[] id = ids.split(",");
		int l = id.length;
		if (l > 0) {
			for (int i = 0; i < l; i++) {
				if (id[i].length() > 0) {
					db.or().eq(pkString, id[i]);
				}
			}
			db.enableCheck();// 开启权限检查
			rl = rb ? db.deleteAll() : -1;
		} else {
			rb = false;
		}
		return rMsg.netMSG(rb, rl);
	}

	/**
	 * 获取管理员信息
	 * 
	 * @param info
	 * @return
	 */
	public String get(String id) {
		if (!StringHelper.InvaildString(id)) {
			return rMsg.netMSG(false, "无效id");
		}
		return rMsg.netMSG(true, db.eq(pkString, id).find());
	}

	/**
	 * 获得全部管理员信息
	 * 
	 * @return
	 */
	public String getAll() {
		return rMsg.netMSG(true, db.select());
	}

	/**
	 * 分页方式获得管理员信息
	 * 
	 * @param idx
	 * @param max
	 * @return
	 */
	public String page(int idx, int max) {
		if (idx <= 0) {
			return rMsg.netMSG(false, "页码错误");
		}
		if (max <= 0) {
			return rMsg.netMSG(false, "页长度错误");
		}
		return rMsg.netPAGE(idx, max, db.dirty().count(), db.page(idx, max));
	}

	/**
	 * 根据条件获得分页数据
	 * 
	 * @param idx
	 * @param max
	 * @param cond
	 * @return
	 */
	public String pageby(int idx, int max, String cond) {
		String out = null;
		if (idx <= 0) {
			return rMsg.netMSG(false, "页码错误");
		}
		if (max <= 0) {
			return rMsg.netMSG(false, "页长度错误");
		}
		JSONArray condArray = JSONArray.toJSONArray(cond);
		if (condArray != null && condArray.size() > 0) {
			db.where(condArray);
			out = rMsg.netPAGE(idx, max, db.dirty().count(), db.page(idx, max));
		} else {
			out = rMsg.netMSG(false, "无效条件");
		}
		return out;
	}

	/**
	 * 设置接收短信
	 * 
	 * @param eid
	 * @return
	 */
	public String enable(String id) {
		if (!StringHelper.InvaildString(id)) {
			return rMsg.netMSG(false, "非法数据");
		}
		return rMsg.netState(taskState(id, 1));
	}

	/**
	 * 设置不接收短信
	 * 
	 * @param eid
	 * @return
	 */
	public String disable(String id) {
		if (!StringHelper.InvaildString(id)) {
			return rMsg.netMSG(false, "非法数据");
		}
		return rMsg.netState(taskState(id, 0));
	}

	private boolean taskState(String id, int state) {
		if (!StringHelper.InvaildString(id)) {
			return false;
		}
		JSONObject obj = new JSONObject("state", state);
		obj = db.eq(pkString, id).data(obj).update();
		return obj != null;
	}
}
