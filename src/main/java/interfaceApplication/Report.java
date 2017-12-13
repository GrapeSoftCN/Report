package interfaceApplication;

import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.ObjectUtils.Null;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import JGrapeSystem.rMsg;
import Model.CommonModel;
import apps.appIns;
import apps.appsProxy;
import authority.plvDef.UserMode;
import cache.CacheHelper;
import check.checkHelper;
import checkCode.checkCodeHelper;
import interfaceModel.GrapeDBSpecField;
import interfaceModel.GrapeTreeDBModel;
import interrupt.interrupt;
import nlogger.nlogger;
import offices.excelHelper;
import security.codec;
import session.session;
import sms.ruoyaMASDB;
import string.StringHelper;
import thirdsdk.wechatHelper;
import time.TimeHelper;

public class Report {
	private GrapeTreeDBModel report;
	private GrapeDBSpecField gDbSpecField;
	private CommonModel model;
	private CacheHelper cache;
	private session se;
	private JSONObject userInfo = null;
	private String currentWeb = null;
	private Integer userType = 0;
	private String userid = null;
	private String appid = null;
	private String wechatAppid = "";
	private static ExecutorService rs = Executors.newFixedThreadPool(300);

	public Report() {
		appid = appsProxy.appidString();
		model = new CommonModel();
		cache = new CacheHelper();

		report = new GrapeTreeDBModel();
		gDbSpecField = new GrapeDBSpecField();
		gDbSpecField.importDescription(appsProxy.tableConfig("Report"));
		report.descriptionModel(gDbSpecField);
		report.bindApp();

		se = new session();
		userInfo = se.getDatas();
		if (userInfo != null && userInfo.size() != 0) {
			currentWeb = userInfo.getString("currentWeb"); // 当前站点id
			userid = userInfo.getMongoID("_id");
			userType = userInfo.getInt("userType");
		}
	}

	/**
	 * 获取微信用户信息
	 * 
	 * @param sdkUserID
	 * @param openid
	 * @return
	 */
	protected JSONObject getWechatUserInfos(int sdkUserID, String openid) {
		JSONObject WechatuserInfo = null;
		wechatHelper wechatHelper = model.getWeChatHelper(sdkUserID);
		if (wechatHelper != null) {
			WechatuserInfo = wechatHelper.getUserInfo(openid);
		}
		return WechatuserInfo;
	}

	/**
	 * 实名认证
	 * 
	 * @param info
	 * @return
	 */
	public String Certification(int sdkUserID, String info) {
		String openid = "", phone = "";
		String result = rMsg.netMSG(100, "验证码发送失败");
		JSONObject object = JSONObject.toJSON(info);
		if (object == null || object.size() <= 0) {
			return rMsg.netMSG(1, "无效参数");
		}
		if (object.containsKey("openid")) {
			openid = object.getString("openid");
		}
		if (!StringHelper.InvaildString(openid)) {
			return rMsg.netMSG(1, "未获取到该用户信息");
		}
		if (object.containsKey("phone")) {
			phone = object.getString("phone");
		}
		if (!StringHelper.InvaildString(phone) && !checkHelper.checkMobileNumber(phone)) {
			return rMsg.netMSG(1, "未填写手机号或者手机号填写错误");
		}
		String ckcode = checkCodeHelper.generateVerifyCode(6); // 获取6位验证码
		String tip = ruoyaMASDB.sendSMS(phone, "验证码为: " + ckcode);
		if (tip != null) {
			result = rMsg.netMSG(0, "验证码发送成功");
			appIns env = appsProxy.getCurrentAppInfo();
			rs.execute(() -> {
				String phones = object.getString("phone");
				appsProxy.setCurrentAppInfo(env);
				String nextstep = appid + "/GrapeReport/WechatUser/insertOpenId/int:" + sdkUserID + "/" + codec.encodeFastJSON(info);
				interrupt._break(ckcode.toLowerCase(), phones, nextstep);
			});
		}
		return result;
	}

	/**
	 * 实名认证 验证短信验证码 验证码大小写不敏感
	 * 
	 * @param info
	 * @return
	 */
	public String resume(String info) {
		String result = rMsg.netMSG(2, "验证码已失效，请重新获取验证码");
		CacheHelper ch = new CacheHelper();
		JSONObject object = JSONObject.toJSON(info);
		if (object == null || object.size() <= 0) {
			return rMsg.netMSG(1, "无效参数");
		}
		if (object.containsKey("phone") && object.containsKey("ckcode")) {
			int code = interrupt._resume(object.getString("ckcode").toLowerCase(), object.getString("phone"));
			if (code == 2) { // 执行成功
				result = rMsg.netMSG(0, "实名认证成功");
				// 判断缓存中是否存在需要新增的举报信息
				if (object.containsKey("openid")) {
					String openid = object.getString("openid");
					String reportInfo = ch.get("openid");
					if (reportInfo != null) {
						result = insert(reportInfo);
						ch.delete(openid); // 新增举报信息，同时删除缓存中的数据
					}
				}
			}
		}
		return result;
	}

	/**
	 * 查询个人举报件
	 * 
	 * @param idx
	 * @param pageSize
	 * @return
	 */
	public String showByUser(int idx, int pageSize) {
		long total = 0;
		JSONArray array = null;
		try {
			if (!StringHelper.InvaildString(userid)) {
				return rMsg.netMSG(1, "当前登录信息失效，无法查看信息");
			}
			report.eq("userid", userid);
			array = report.dirty().field("_id,content,time,handletime,completetime,refusetime,state,reason").dirty().desc("time").page(idx, pageSize);
			total = report.count();
			report.clear();
		} catch (Exception e) {
			nlogger.logout(e);
			array = null;
		}
		return rMsg.netPAGE(idx, pageSize, total, (array != null && array.size() > 0) ? model.dencode(array) : new JSONArray());
	}

	/**
	 * 查询个人举报件
	 * 
	 * @param idx
	 * @param pageSize
	 * @return
	 */
	public String searchByUser(String userid, int no) {
		JSONArray array = null;
		try {
			array = new JSONArray();
			array = report.desc("time").eq("userid", userid).ne("state", Integer.valueOf(0)).limit(no).select();
		} catch (Exception e) {
			array = null;
		}
		array = model.dencode(array);
		array = model.getImage(array);
		return rMsg.netMSG(true, array);
	}

	/**
	 * 网页端新增举报
	 * 
	 * @param info
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public String AddReportWeb(String info) {
		Object rs = null;
		String wbid = currentWeb;
		long currentTime = TimeHelper.nowMillis();
		long time = currentTime;
		String result = rMsg.netMSG(100, "新增举报件失败");
		try {
			JSONObject object = JSONObject.toJSON(info);
			if (object == null || object.size() <= 0) {
				return rMsg.netMSG(1, "无效参数");
			}
			String content = object.getString("content");
			content = codec.DecodeHtmlTag(content);
			object.put("content", content);
			if (object.containsKey("wbid")) {
				wbid = object.getString("wbid");
			}
			object.put("circulation", wbid);
			if (object.containsKey("time")) {
				time = object.getLong(time);
				if (time == 0 || time > currentTime) {
					time = currentTime;
				}
			}
			object.put("time", time);
			rs = report.data(object).autoComplete().insertOnce();
			result = rs != null ? rMsg.netMSG(0, "提交举报信息成功") : result;
			// 发送数据到kafka
			appsProxy.proxyCall("/GrapeSendKafka/SendKafka/sendData2Kafka/" + rs + "/int:2/int:1/int:0");
		} catch (Exception e) {
			nlogger.logout(e);
			result = rMsg.netMSG(100, "提交举报信息失败");
		}
		return result;
	}

	/**
	 * 获取用户openid，实名认证
	 * 
	 * @param code
	 * @param url
	 * @return
	 * 
	 */
	@SuppressWarnings("unchecked")
	public String getUserId(String code, String url, int sdkUserID) {
		String sign = "", openId = "", headImage = "";
		JSONObject object = new JSONObject();
		if (!StringHelper.InvaildString(code) || !StringHelper.InvaildString(url)) {
			return rMsg.netMSG(false, "无效code");
		}
		wechatHelper wechatHelper = model.getWeChatHelper(sdkUserID);
		if (wechatHelper == null) {
			return rMsg.netMSG(false, "无效sdkUserId");
		}
		try {
			// 获取微信签名
			url = codec.DecodeHtmlTag(url);
			url = URLEncoder.encode(url, "utf-8");
			sign = wechatHelper.signature(url);
			JSONObject signObj = JSONObject.toJSON(sign);
			if (signObj == null || signObj.size() <=0) {
				return rMsg.netMSG(false, "无效url");
			}
			signObj.put("appid", model.getwechatAppid(sdkUserID, "appid"));
			// 获取openid
			openId = wechatHelper.getOpenID(code);
			if (!StringHelper.InvaildString(openId)) {
				return rMsg.netMSG(false, "无法获取用户微信openid");
			}
			object.put("openid", openId);
			object.put("sign", signObj.toString());
			object.put("headimgurl", headImage);
			JSONObject userInfos = new WechatUser().FindOpenId(openId);
			if (userInfos != null && userInfos.size() > 0) {
				object.put("msg", "已实名认证");
				headImage = userInfos.getString("headimgurl");
				object.put("headimgurl", headImage);
				return rMsg.netMSG(0, object);
			}
		} catch (Exception e) {
			nlogger.logout(e);
		}
		object.put("msg", "未实名认证");
		return rMsg.netMSG(1, object);
	}

	/**
	 * 新增举报件
	 * 
	 * @param sdkUserId
	 * @param info
	 * @return
	 */
	public String AddReport(String info) {
		int mode = 1; // 默认匿名举报
		String userid = "";
		String result = rMsg.netMSG(100, "新增举报件失败");
		info = checkParam(info);
		if (info.contains("errorcode")) {
			return info;
		}
		JSONObject object = JSONObject.toJSON(info);
		if (object != null && object.size() > 0) {
			if (object.containsKey("mode")) {
				mode = Integer.parseInt(object.getString("mode"));
			}
			if (object.containsKey("userid")) {
				userid = object.getString("userid");
			}
			if (!StringHelper.InvaildString(userid)) {
				return rMsg.netMSG(false, "无效openid");
			}
			switch (mode) {
			case 0: // 实名
				result = NonAnonymous(userid, info);
				break;
			case 1: // 匿名
				result = insert(codec.encodeFastJSON(info));
				break;
			}
		}
		return result;
	}

	/**
	 * 实名举报
	 * 
	 * @param userid
	 *            用户openid
	 * @param object
	 *            用户提交的举报件信息
	 * @return
	 */
	private String NonAnonymous(String userid, String info) {
		String result = rMsg.netMSG(100, "提交失败");
		// 判断当前用户是否已实名认证过，未实名认证，则进行实名认证，并将举报信息存入缓存
		JSONObject obj = new WechatUser().FindOpenId(userid);
		if (obj == null || obj.size() <= 0) {
			cache.setget(userid, codec.encodeFastJSON(info), 10 * 3600);
			return rMsg.netMSG(4, "您还未实名认证,请实名认证");
		}
		result = RealName(info, obj);
		return result;
	}

	/**
	 * 实名认证，发送短信到用户
	 * 
	 * @param object
	 * @param userInfos
	 * @return
	 */
	private String RealName(String info, JSONObject userInfos) {
		String phone = "", result = rMsg.netMSG(100, "获取验证码失败");
		JSONObject temp;
		String ckcode = checkCodeHelper.generateVerifyCode(6);
		if (userInfos != null && userInfos.size() > 0) {
			phone = userInfos.getString("phone");
		}
		if (StringHelper.InvaildString(phone)) {
			// 发送短信
			result = SendVerity(phone, "验证码为：" + ckcode);
			if (StringHelper.InvaildString(result)) {
				temp = JSONObject.toJSON(result);
				if (temp.getLong("errorcode") == 0) { // 发送短信息成功
					appIns env = appsProxy.getCurrentAppInfo();
					rs.execute(() -> {
						String phoneNo = userInfos.getString("phone");
						appsProxy.setCurrentAppInfo(env);
						String nextStep = appid + "/GrapeReport/Report/insert/" + codec.encodeFastJSON(info);
						interrupt._break(ckcode, phoneNo, nextStep);
					});
				}
			}
		}
		return result;
	}

	/**
	 * 发送短信验证码，每人每天5次 存入缓存 key：phone；value:{"time":day,"count":0}
	 * 
	 * @param phone
	 * @param text
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private String SendVerity(String phone, String text) {
		JSONObject obj;
		String tip = null;
		String result = rMsg.netMSG(2, "短信发送失败");
		int day = 0, count = 0, currentDay = TimeHelper.getNowDay();
		CacheHelper ch = new CacheHelper();
		if (ch.get(phone) != null) {
			obj = JSONObject.toJSON(ch.get(phone));
			day = obj.getInt("time");
			if (day == currentDay && count == 5) {
				return rMsg.netMSG(1, "您今日短信发送次数已达上线");
			}
		}
		// 直接存入缓存
		tip = ruoyaMASDB.sendSMS(phone, text);
		if (StringHelper.InvaildString(tip)) {
			count++;
			ch.setget(phone, new JSONObject("count", count).put("time", currentDay), 86400);
			result = rMsg.netMSG(0, "短信发送成功");
		}
		return result;
	}

	/**
	 * 举报件处理完成
	 * 
	 * @param id
	 * @param reson
	 * @return
	 */
	public String CompleteReport(String id, String reson) {
		int code = 99;
		JSONObject object = new JSONObject();
		if (StringHelper.InvaildString(reson)) {
			object = JSONObject.toJSON(reson);
			if (object == null || object.size() <= 0) {
				return rMsg.netMSG(1, "无效参数");
			}
		}
		code = OperaReport(id, object, 2);
		// 发送数据到kafka
		appsProxy.proxyCall("/GrapeSendKafka/SendKafka/sendData2Kafka/" + id + "/int:2/int:3/int:3");
		return code == 0 ? rMsg.netMSG(0, "") : rMsg.netMSG(100, "");
	}

	/**
	 * 举报拒绝
	 * 
	 * @param id
	 * @param reson
	 * @return
	 */
	public String RefuseReport(String id, String reson) {
		int code = 99;
		JSONObject object = new JSONObject();
		if (StringHelper.InvaildString(reson)) {
			object = JSONObject.toJSON(reson);
			if (object == null || object.size() <= 0) {
				if (object == null || object.size() < 0) {
					return rMsg.netMSG(1, "无效参数");
				}
			}
		}
		code = OperaReport(id, object, 3);
		// 发送数据到kafka
		appsProxy.proxyCall("/GrapeSendKafka/SendKafka/sendData2Kafka/" + id + "/int:2/int:3/int:4");
		return code == 0 ? rMsg.netMSG(0, "") : rMsg.netMSG(100, "");
	}

	/**
	 * 举报件正在处理
	 * 
	 * @param id
	 * @param typeInfo
	 * @return
	 */
	public String HandleReport(String id, String typeInfo) {
		int code = 99;
		JSONObject object = new JSONObject();
		if (StringHelper.InvaildString(typeInfo)) {
			object = JSONObject.toJSON(typeInfo);
			if (object == null || object.size() < 0) {
				return rMsg.netMSG(1, "无效参数");
			}
		}
		code = OperaReport(id, object, 1);
		// 发送数据到kafka
		appsProxy.proxyCall("/GrapeSendKafka/SendKafka/sendData2Kafka/" + id + "/int:2/int:3/int:2");
		return code == 0 ? rMsg.netMSG(0, "") : rMsg.netMSG(100, "");
	}

	/**
	 * 正在处理，完成，拒绝操作
	 * 
	 * @param id
	 * @param object
	 * @param type
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private int OperaReport(String id, JSONObject object, int type) {
		int code = 99;
		if (object != null && object.size() > 0) {
			switch (type) {
			case 1: // 正在处理
				object.put("handletime", TimeHelper.nowMillis());
				object.put("state", 1);
				break;

			case 2: // 处理完成
				object.put("completetime", TimeHelper.nowMillis());
				object.put("state", 2);
				break;
			case 3: // 拒绝处理
				object.put("completetime", TimeHelper.nowMillis());
				object.put("state", 3);
				object.put("isdelete", 1);
				break;
			}
			code = report.eq("_id", id).data(object).update() != null ? 0 : 99;

		}
		return code;
	}

	/* 前台分页显示 */
	public String PageFront(String wbid, int idx, int pageSize, String info) {
		long total = 0;
		if (StringHelper.InvaildString(info)) {
			JSONArray condArray = model.buildCond(info);
			if (condArray != null && condArray.size() > 0) {
				report.where(condArray);
			} else {
				return rMsg.netPAGE(idx, pageSize, total, new JSONArray());
			}
		}
		report.eq("wbid", wbid);
		JSONArray array = report.dirty().desc("time").desc("_id").page(idx, pageSize);
		total = report.count();
		array = model.getImage(model.dencode(array));
		return rMsg.netPAGE(idx, pageSize, total, (array != null && array.size() > 0) ? array : new JSONArray());
	}

	// 分页
	public String PageReport(int idx, int pageSize) {
		return PageBy(idx, pageSize, null);
	}

	public String PageByReport(int ids, int pageSize, String info) {
		return PageBy(ids, pageSize, info);
	}

	public String PageBy(int idx, int pageSize, String info) {
		long total = 0;
		if (!StringHelper.InvaildString(currentWeb)) {
			return rMsg.netMSG(1, "当前登录已失效，无法查看举报信息");
			// return rMsg.netPAGE(idx, pageSize, total, new JSONArray());
		}
		if (UserMode.root > userType && userType >= UserMode.admin) { // 判断是否是网站管理员
			String[] webtree = getAllWeb();
			if (webtree != null && !webtree.equals("")) {
				report.or();
				for (String string : webtree) {
					report.eq("wbid", string);
				}
			}
			// report.and();
			// report.eq("circulation", currentWeb);
		}
		if (StringHelper.InvaildString(info)) {
			JSONArray condArray = model.buildCond(info);
			if (condArray != null && condArray.size() > 0) {
				report.where(condArray);
			} else {
				return rMsg.netPAGE(idx, pageSize, total, new JSONArray());
			}
		}
		JSONArray array = report.dirty().desc("time").page(idx, pageSize);
		total = report.count();
		model.dencode(array);
		array = model.getImage(array);
		return rMsg.netPAGE(idx, pageSize, total, (array != null && array.size() > 0) ? array : new JSONArray());
	}

	// 尚未被处理的事件总数
	public String Count() {
		long count = 0;
		// 判断当前用户身份：系统管理员，网站管理员
		userType = 1000;
		if (UserMode.root > userType && userType >= UserMode.admin) { // 判断是否是网站管理员
			// 网站管理员
			if (StringHelper.InvaildString(currentWeb)) {
				String[] webtree = getAllWeb();
				if (webtree != null && !webtree.equals("")) {
					for (String string : webtree) {
						count += report.eq("wbid", string).eq("state", 0).count();
					}
				}
			}
		} else if (userType >= UserMode.root) {
			// 系统管理员统计所有的未处理的举报件信息
			count = report.eq("state", 0).count();
		}
		return rMsg.netMSG(0, String.valueOf(count));
	}

	/**
	 * 举报信息流转，只能流转到下级，且由一级流转至二级
	 * 
	 * @project GrapeReport
	 * @package interfaceApplication
	 * @file Report.java
	 * 
	 * @param rid
	 *            当前举报件id
	 * @param targetWeb
	 *            流转目标网站id
	 * @return
	 *
	 */
	@SuppressWarnings("unchecked")
	public String circulationReport(String rid, String targetWeb) {
		String currentId = "";
		int code = 99;
		String result = rMsg.netMSG(100, "当前站点不支持转发");
		JSONObject obj = new JSONObject();
		obj.put("circulation", targetWeb);
		if (StringHelper.InvaildString(currentWeb)) {
			// 判断当前网站是否为一级网站
			if (IsFirstWeb(currentId)) {
				code = report.eq("_id", rid).data(obj).update() != null ? 0 : 99;
			}
			result = code == 0 ? rMsg.netMSG(0, "已经流转至其他用户") : result;
		} else {
			result = rMsg.netMSG(99, "当前用户信息已失效，请重新登录");
		}
		return result;
	}

	/**
	 * 判断是否为一级网站
	 * 
	 * @project GrapeReport
	 * @package interfaceApplication
	 * @file Report.java
	 * 
	 * @param currentId
	 * @return
	 *
	 */
	private boolean IsFirstWeb(String currentId) {
		JSONObject tempobj = null;
		String temp = (String) appsProxy.proxyCall("/GrapeWebInfo/WebInfo/isFirstWeb/" + currentWeb);
		if (temp != null && temp.length() > 0) {
			tempobj = JSONObject.toJSON(temp);
		}
		return (tempobj != null && tempobj.size() > 0);
	}

	/**
	 * 导出举报件信息
	 * 
	 * @param info
	 *            查询条件
	 * @param file
	 *            导出文件名称
	 * @return
	 */
	public Object Export(String info, String file) {
		String reportInfo = searchExportInfo(info);
		if (StringHelper.InvaildString(reportInfo)) {
			try {
				return excelHelper.out(reportInfo);
			} catch (Exception e) {
				nlogger.logout(e);
			}
		}
		return rMsg.netMSG(false, "导出异常");
	}

	/**
	 * 查询举报件信息
	 * 
	 * @param info
	 * @return
	 *
	 */
	private String searchExportInfo(String info) {
		JSONArray condArray = null;
		JSONArray array = null;
		if (!StringHelper.InvaildString(info)) {
			condArray = JSONArray.toJSONArray(info);
			if (condArray != null && condArray.size() != 0) {
				report.where(condArray);
			} else {
				return null;
			}
		}
		array = report.field("userid,Wrongdoer,content,slevel,mode,state,time,handletime,refusetime,completetime,attr").select();
		return (array != null && array.size() != 0) ? array.toJSONString() : null;
	}

	/**
	 * 获取当前站点的所有下级站点，包含当前站点
	 * 
	 * @return
	 */
	private String[] getAllWeb() {
		String[] webtree = null;
		if (StringHelper.InvaildString(currentWeb)) {
			String webTree = (String) appsProxy.proxyCall("/GrapeWebInfo/WebInfo/getWebTree/" + currentWeb);
			webtree = webTree.split(",");
		}
		return webtree;
	}

	/**
	 * 查询举报件详情
	 * 
	 * @param id
	 * @return
	 */
	public String SearchById(String id) {
		if (!StringHelper.InvaildString(id)) {
			return rMsg.netMSG(false, "无效举报件id");
		}
		JSONObject object = Search(id);
		// // 发送数据到kafka
		// appsProxy.proxyCall("/GrapeSendKafka/SendKafka/sendData2Kafka/" + id
		// + "/int:2/int:2/int:0");
		return rMsg.netMSG(true, model.getImage(object));
	}

	/**
	 * 查询举报件信息
	 * 
	 * @param id
	 * @return
	 */
	private JSONObject Search(String id) {
		JSONObject object = null;
		if (StringHelper.InvaildString(id)) {
			object = report.eq("_id", id).find();
		}
		return (object != null && object.size() > 0) ? model.dencode(object) : null;
	}

	/**
	 * 查询举报件信息
	 * 
	 * @param id
	 * @return
	 */
	public String SearchReport(int idx, int pageSize, String condString) {
		long total = 0;
		JSONArray array = null;
		JSONArray condArray = model.buildCond(condString);
		if (condArray != null && condArray.size() > 0) {
			report.where(condArray);
		} else {
			return rMsg.netMSG(1, "无效条件");
		}
		total = report.dirty().count();
		array = report.page(idx, pageSize);
		return rMsg.netPAGE(idx, pageSize, total, array);
	}

	/**
	 * 查询个人相关的举报件的总数
	 * 
	 * @param userid
	 * @return
	 */
	public String CountById(String userid) {
		long count = 0;
		if (!StringHelper.InvaildString(userid)) {
			return rMsg.netMSG(1, "无效用户id");
		}
		count = report.eq("userid", userid).count();
		return rMsg.netMSG(true, count);
	}

	/**
	 * 统计待处理举报
	 * 
	 * @return
	 */
	public String CountReport() {
		String wbid = "";
		long count = 0;
		report.eq("state", 0);
		if (StringHelper.InvaildString(currentWeb)) {
			count = report.eq("wbid", wbid).count();
		}
		return rMsg.netMSG(0, String.valueOf(count));
	}

	/**
	 * 新增操作
	 * 
	 * @param info
	 * @return
	 */
	public String insert(String info) {
		String tip = null;
		JSONObject object = null;
		String result = rMsg.netMSG(100, "提交举报失败");
		info = codec.DecodeFastJSON(info);
		if (!StringHelper.InvaildString(info)) {
			return rMsg.netMSG(false, "无效参数");
		}
		tip = report.data(info).autoComplete().insertOnce().toString();
		object = Search(tip);
		return (object != null && object.size() > 0) ? rMsg.netMSG(0, object) : result;
	}

	/**
	 * 新增举报件，参数验证编码
	 * 
	 * @param info
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private String checkParam(String info) {
		long time = 0;
		String result = rMsg.netMSG(1, "参数异常");
		if (StringHelper.InvaildString(info)) {
			info = codec.DecodeFastJSON(info);
			JSONObject object = JSONObject.toJSON(info);
			if (object != null && object.size() > 0) {
				if (object.containsKey("content")) {
					String content = object.get("content").toString();
					if (StringHelper.InvaildString(content)) {
						if (content.length() > 500) {
							return rMsg.netMSG(2, "举报内容超过指定字数");
						}
						object.put("content", codec.encodebase64(content));
					}
				}
				if (object.containsKey("time")) {
					time = object.getLong("time");
				}
				if (time == 0) {
					time = TimeHelper.nowMillis();
				}
				object.put("time", time);
				result = object.toJSONString();
			}
		}
		return result;
	}

	/**
	 * 对用户进行封号
	 * 
	 * @param openid
	 * @param info
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public String kick(String openid, String info) {
		int code = 99;
		String result = rMsg.netMSG(100, "操作失败");
		JSONObject obj = new JSONObject();
		JSONObject object = JSONObject.toJSON(info);
		if (!object.containsKey("_id")) {
			return rMsg.netMSG(2, "无法获取待处理举报信息");
		}
		result = new WechatUser().kickUser(openid, info);
		if (JSONObject.toJSON(result).getLong("errorcode") == 0) {
			if (object.containsKey("reason")) {
				obj.put("reason", object.getString("reason"));
			}
			code = OperaReport(object.getString("_id"), obj, 3);
			result = (code == 0) ? rMsg.netMSG(0, "操作成功") : result;
		}
		return result;
	}

	/**
	 * 统计已提交举报件增量 按时间区间查询 结束时间为当前时间 开始时间为当前时间减去管理员设置的接收短信的间隔时间
	 * 
	 * @param timediff
	 *            间隔时间
	 * @return
	 */
	protected long getReportCount(long timediff) {
		long count = 0;
		long currentTime = TimeHelper.nowMillis();
		long startTime = currentTime - timediff;
		count = report.gt("time", startTime).lt("time", currentTime).eq("state", 1).count();
		return count;
	}
}
