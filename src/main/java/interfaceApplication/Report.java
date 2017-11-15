package interfaceApplication;

import java.io.File;
import java.io.FileOutputStream;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import JGrapeSystem.rMsg;
import Model.CommonModel;
import apps.appsProxy;
import authority.plvDef.plvType;
import cache.CacheHelper;
import checkCode.checkCodeHelper;
import file.fileHelper;
import interfaceModel.GrapeDBSpecField;
import interfaceModel.GrapeTreeDBModel;
import interrupt.interrupt;
import nlogger.nlogger;
import offices.excelHelper;
import security.codec;
import session.session;
import sms.ruoyaMASDB;
import string.StringHelper;
import time.TimeHelper;

public class Report {
    private GrapeTreeDBModel report;
    private GrapeDBSpecField gDbSpecField;
    private CommonModel model;
    private CacheHelper cache;
    private session se;
    private JSONObject userInfo = null;
    private String currentWeb = null;
    private String appid = null;
    private Integer userType = null;

    public Report() {
        appid = appsProxy.appidString();
        model = new CommonModel();
        cache = new CacheHelper();

        report = new GrapeTreeDBModel();
        gDbSpecField = new GrapeDBSpecField();
        gDbSpecField.importDescription(appsProxy.tableConfig("Report"));
        report.descriptionModel(gDbSpecField);
        report.bindApp();
        report.enableCheck();//开启权限检查

        se = new session();
        userInfo = se.getDatas();
        if (userInfo != null && userInfo.size() != 0) {
            currentWeb = userInfo.getString("currentWeb"); // 当前站点id
            userType =userInfo.getInt("userType");//当前用户身份
        }
    }

    /**
     * 新增举报件
     * 
     * @param sdkUserId
     * @param info
     * @return
     */
    public String AddReport(String sdkUserId, String info) {
        int mode = 0;
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
            switch (mode) {
            case 0: // 实名
                result = NonAnonymous(userid, object);
                break;
            case 1: // 匿名
                result = Anonymous(object);
                break;
            }
        }
        return result;
    }

    /**
     * 实名认证成功之后，继续提交举报信息
     * 
     * @param info
     * @return
     */
    @SuppressWarnings("unchecked")
    public String resume(String info) {
        String openid = "";
        String result = rMsg.netMSG(100, "新增举报件失败");
        if (!StringHelper.InvaildString(info)) {
            JSONObject object = JSONObject.toJSON(info);
            if (object != null && object.size() > 0) {
                openid = object.getString("userid");
                JSONObject object2 = new WechatUser().FindOpenId(openid);
                if (object2 != null && object2.size() > 0) {
                    if (object2.containsKey("phone")) {
                        object.put("phone", object2.getString("phone"));
                    }
                }
                if (object.containsKey("phone") && object.containsKey("ckcode")) {
                    int code = interrupt._resume(object.get("ckcode").toString(), object.get("phone").toString(), appid);
                    result = (code == 1) ? rMsg.netMSG(6, "验证码错误") : (code == 2 ? rMsg.netMSG(0, object2) : result);
                }
            }
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
        if (!StringHelper.InvaildString(reson)) {
            JSONObject object = JSONObject.toJSON(reson);
            if (object != null && object.size() > 0) {
                code = OperaReport(id, object, 2);
            }
        }
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
        if (!StringHelper.InvaildString(reson)) {
            JSONObject object = JSONObject.toJSON(reson);
            if (object != null && object.size() > 0) {
                code = OperaReport(id, object, 3);
            }
        }
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
        if (!StringHelper.InvaildString(typeInfo)) {
            JSONObject object = JSONObject.toJSON(typeInfo);
            if (object != null && object.size() > 0) {
                code = OperaReport(id, object, 1);
            }
        }
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
        return code;
    }

    /* 前台分页显示 */
    public String PageFront(String wbid, int idx, int pageSize, String info) {
        long total = 0;
        if (!StringHelper.InvaildString(info)) {
            JSONArray condArray = model.buildCond(info);
            if (condArray != null && condArray.size() > 0) {
                report.where(condArray);
            } else {
                return rMsg.netPAGE(idx, pageSize, total, new JSONArray());
            }
        }
        report.eq("wbid", wbid);
        JSONArray array = report.dirty().page(idx, pageSize);
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
        if (StringHelper.InvaildString(currentWeb)) {
            return rMsg.netPAGE(idx, pageSize, total, new JSONArray());
        }
        if (true) { // 判断是否为网站管理员
            String[] webtree = getAllWeb();
            if (webtree != null && !webtree.equals("")) {
                report.or();
                for (String string : webtree) {
                    report.eq("wbid", string);
                }
            }
            report.and();
            report.eq("circulation", currentWeb);
        }
        if (!StringHelper.InvaildString(info)) {
            JSONArray condArray = model.buildCond(info);
            if (condArray != null && condArray.size() > 0) {
                report.where(condArray);
            } else {
                return rMsg.netPAGE(idx, pageSize, total, new JSONArray());
            }
        }
        JSONArray array = report.dirty().page(idx, pageSize);
        total = report.count();
        array = model.getImage(model.dencode(array));
        return rMsg.netPAGE(idx, pageSize, total, (array != null && array.size() > 0) ? array : new JSONArray());
    }

    // 尚未被处理的事件总数
    public String Count() {

        long count = 0;
        // 系统管理员统计所有的未处理的举报件信息
        count = report.eq("state", 0).count();
        // 网站管理员
        if (!StringHelper.InvaildString(currentWeb)) {
            String[] webtree = getAllWeb();
            if (webtree != null && !webtree.equals("")) {
                for (String string : webtree) {
                    count += report.eq("wbid", string).eq("state", 0).count();
                }
            }
        }
        return rMsg.netMSG(0, String.valueOf(count));
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
        Object ro = null;
        byte[] by = null;
        String errStr = "";
        String infos = null;
        String dir = model.getConfigString("filepath");
        String path = TimeHelper.stampToDate(TimeHelper.nowMillis()).split(" ")[0];
        path = dir + path + "//" + file;
        if (!StringHelper.InvaildString(info)) {
            infos = searchExportInfo(info);
        }
        if (!StringHelper.InvaildString(infos)) {
            try {
                if (!new File(path).exists()) {
                    fileHelper.createFile(path);
                }
                by = excelHelper.out(infos);
                FileOutputStream fos = new FileOutputStream(path);
                fos.write(by);
                fos.close();
                ro = rMsg.netMSG(0, model.getImageUri(path));
            } catch (Exception e) {
                nlogger.logout(e);
                errStr = "导出举报信息失败";
                ro = rMsg.netMSG(99, errStr);
            }
        }
        return ro;
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
        // array = model.getImg(array);
        // array = cleanUpData(array);
        return (array != null && array.size() != 0) ? array.toJSONString() : null;
    }

    /**
     * 获取当前站点的所有下级站点，包含当前站点
     * 
     * @return
     */
    private String[] getAllWeb() {
        String[] webtree = null;
        if (!StringHelper.InvaildString(currentWeb)) {
            String webTree = (String) appsProxy.proxyCall("/GrapeWebInfo/WebInfo/getChildwebs/" + currentWeb);
            webtree = webTree.split(",");
        }
        return webtree;
    }

    /**
     * 实名举报
     * 
     * @param userid
     * @param object
     * @return
     */
    private String NonAnonymous(String userid, JSONObject object) {
        String result = rMsg.netMSG(100, "新增举报件失败");
        try {
            JSONObject object2 = new WechatUser().FindOpenId(userid);
            if (object2 == null || object2.size() == 0) {
                cache.setget(userid, object.toString());
                return rMsg.netMSG(4, "您还未实名认证,请先实名认证");
            }
            result = RealName(object);
        } catch (Exception e) {
            nlogger.logout(e);
            result = rMsg.netMSG(100, "新增举报件失败");
        }
        return result;
    }

    /**
     * 发送验证码到用户
     * 
     * @param object
     * @return
     */
    private String RealName(JSONObject object) {
        int code = 99;
        String openid = object.get("userid").toString();
        String result = rMsg.netMSG(100, "验证码发送失败");
        JSONObject object2 = new WechatUser().FindOpenId(openid);
        if (object2 != null && object2.size() > 0) {
            String phone = object2.get("phone").toString();
            String ckcode = checkCodeHelper.getCheckCode(phone, 6);
            // 1.发送验证码
            code = SendVerity(phone, "验证码为：" + ckcode + "有效时间为30秒，请在有效时间内输入验证码");
            switch (code) {
            case 0:
                String nextstep = appid + "/GrapeReport/Report/insert/" + object.toString();
                boolean flag = interrupt._break(ckcode, phone, nextstep, String.valueOf(appid));
                code = flag ? 0 : 99;
                result = code == 0 ? rMsg.netMSG(0, "验证码发送成功") : result;
                break;
            case 5:
                result = rMsg.netMSG(5, "您今日短信发送次数已达上线");
                break;
            }
        }
        return result;
    }

    /**
     * 发送短信验证码，每人每天5次
     * 
     * @param phone
     * @param text
     * @return
     */
    @SuppressWarnings("unchecked")
    private int SendVerity(String phone, String text) {
        int day = 0;
        int count = 0; // 发送短信次数
        int currentDay = TimeHelper.getNowDay(); // 当前日期
        String tip = null;
        try {
            JSONObject object = new JSONObject();
            if (cache.get(phone) != null) {
                object = JSONObject.toJSON(cache.get(phone));
                if (object != null && object.size() > 0) {
                    day = Integer.parseInt(object.getString("time"));
                    count = Integer.parseInt(object.getString("count"));
                    if (day == currentDay && count == 5) {
                        return 5;
                    }
                }
            }
            tip = ruoyaMASDB.sendSMS(phone, text);
            if (tip != null) {
                count++;
                object.put("count", count);
                object.put("time", TimeHelper.getNowDay());
                cache.setget(phone, object, 24 * 60 * 60);
            }
        } catch (Exception e) {
            nlogger.logout(e);
            tip = null;
        }
        return tip != null ? 0 : 100;
    }

    /**
     * 匿名举报
     * 
     * @param object
     * @return
     */
    private String Anonymous(JSONObject object) {
        String result = rMsg.netMSG(100, "新增举报件失败");
        String info = insert(object);
        JSONObject obj = SearchById(info);
        return (obj != null && obj.size() > 0) ? rMsg.netMSG(0, obj) : result;
    }

    /**
     * 查询举报件信息
     * 
     * @param id
     * @return
     */
    private JSONObject SearchById(String id) {
        JSONObject object = null;
        if (!StringHelper.InvaildString(id)) {
            object = report.eq("_id", id).find();
        }
        return object;
    }

    /**
     * 新增操作
     * 
     * @param info
     * @return
     */
    private String insert(JSONObject info) {
        String tip = null;
        if (info != null) {
            try {
            	JSONObject rMode = new JSONObject(plvType.chkType, plvType.powerVal).puts(plvType.chkVal, 100);//设置默认查询权限
            	JSONObject uMode = new JSONObject(plvType.chkType, plvType.powerVal).puts(plvType.chkVal, 200);
            	JSONObject dMode = new JSONObject(plvType.chkType, plvType.powerVal).puts(plvType.chkVal, 300);
            	info.put("rMode", rMode.toJSONString()); //添加默认查看权限
            	info.put("uMode", uMode.toJSONString()); //添加默认修改权限
            	info.put("dMode", dMode.toJSONString()); //添加默认删除权限
                tip = report.data(info).insertOnce().toString();
            } catch (Exception e) {
                nlogger.logout(e);
                tip = null;
            }
        }
        return tip != null ? tip : "";
    }

    /**
     * 新增举报件，参数验证编码
     * 
     * @param info
     * @return
     */
    @SuppressWarnings("unchecked")
    private String checkParam(String info) {
        String userid = null;
        String result = rMsg.netMSG(1, "参数异常");
        if (!StringHelper.InvaildString(info)) {
            JSONObject object = JSONObject.toJSON(info);
            if (object != null && object.size() > 0) {
                if (object.containsKey("content")) {
                    String content = object.get("content").toString();
                    if (StringHelper.InvaildString(content)) {
                        if (content.length() > 500) {
                            return rMsg.netMSG(2, "举报内容超过指定字数");
                        }
                        content = codec.DecodeHtmlTag(content);
                        content = codec.decodebase64(content);
                        object.put("content", codec.encodebase64(content));
                    }
                }
                if (object.containsKey("userid")) {
                    userid = object.getString("userid");
                }
                if (StringHelper.InvaildString(userid)) {
                    return rMsg.netMSG(3, "获取用户信息异常，请稍候再试");
                }
                result = object.toJSONString();
            }
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
