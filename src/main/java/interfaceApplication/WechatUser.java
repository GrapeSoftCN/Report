package interfaceApplication;

import org.json.simple.JSONObject;

import JGrapeSystem.rMsg;
import apps.appsProxy;
import cache.CacheHelper;
import check.checkHelper;
import interfaceModel.GrapeDBSpecField;
import interfaceModel.GrapeTreeDBModel;
import nlogger.nlogger;
import security.codec;
import string.StringHelper;
import time.TimeHelper;

public class WechatUser {
    private GrapeTreeDBModel wechatUser;
    private GrapeDBSpecField gDbSpecField;

    public WechatUser() {

        wechatUser = new GrapeTreeDBModel();
        gDbSpecField = new GrapeDBSpecField();
        gDbSpecField.importDescription(appsProxy.tableConfig("WechatUser"));
        wechatUser.descriptionModel(gDbSpecField);
        wechatUser.bindApp();
    }

    /**
     * 实名认证
     * 
     * @param info
     * @return
     */
    public String insertOpenId(String info) {
        int code = 99;
        String result = rMsg.netMSG(100, "实名认证失败");
        CacheHelper helper = new CacheHelper();
        info = CheckParam(info);
        if (info.contains("errorcode")) {
            return info;
        }
        JSONObject object = JSONObject.toJSON(info);
        if (object != null && object.size() > 0) {
            try {
                String openid = "";
                if (object.containsKey("openid")) {
                    openid = object.getString("openid");
                }
                JSONObject users = FindOpenId(openid);
                if (users == null) {
                    code = wechatUser.data(object).insertOnce() != null ? 0 : 99;
                }

                if (helper.get(openid + "Info") != null) {
                    helper.delete(openid + "Info");
                }
                helper.setget(openid + "Info", users);
            } catch (Exception e) {
                nlogger.logout(e);
                code = 99;
            }
        }
        return code == 0 ? rMsg.netMSG(0, "实名认证成功") : result;
    }

    /**
     * 更新用户头像信息
     * 
     * @param openid
     * @param info
     */
    @SuppressWarnings("unchecked")
    protected String UpdateInfo(String openid, String info) {
        int code = 99;
        String result = rMsg.netMSG(100, "更新用户信息失败");
        try {
            JSONObject object = JSONObject.toJSON(info);
            String headimg = (String) object.get("headimgurl");
            headimg = codec.DecodeHtmlTag(headimg);
            object.put("headimgurl", codec.decodebase64(headimg));
            if (object != null && object.size() > 0) {
                code = wechatUser.eq("openid", openid).data(object).update() != null ? 0 : 99;
            }
        } catch (Exception e) {
            nlogger.logout(e);
            code = 99;
        }
        return code == 0 ? rMsg.netMSG(0, "修改成功") : result;
    }

    /**
     * 根据openid查询用户信息
     * 
     * @param openid
     * @return
     */
    protected JSONObject FindOpenId(String openid) {
        JSONObject object = wechatUser.eq("openid", openid).find();
        return (object != null && object.size() > 0) ? object : null;
    }

    /**
     * 参数验证
     * 
     * @param info
     * @return
     */
    @SuppressWarnings("unchecked")
    private String CheckParam(String info) {
        String headimgurl = "";
        if (StringHelper.InvaildString(info)) {
            return rMsg.netMSG(1, "参数异常");
        }
        JSONObject object = JSONObject.toJSON(info);
        if (object != null && object.size() > 0) {
            if (object.containsKey("type")) {
                object.remove("type");
            }
            if (!object.containsKey("isdelete")) {
                object.put("isdelete", "0");
            }
            if (!object.containsKey("time")) { // 操作时间
                object.put("time", TimeHelper.nowMillis());
            }
            object.put("kickTime", ""); // 封号时间：默认为""，0为永久封号
            if (object.containsKey("phone")) {
                if (!checkHelper.checkMobileNumber(object.get("phone").toString())) {
                    return rMsg.netMSG(2, "手机号格式错误"); // 手机号格式错误
                }
            }
            if (object.containsKey("openid")) {
                String openid = object.get("openid").toString();
                // 获取微信用户的头像信息
                String userinfo = appsProxy.proxyCall("/GrapeWechat/Wechat/getUserInfo/s:" + openid)
                        .toString();
                if (userinfo.contains("headimgurl")) {
                    headimgurl = JSONObject.toJSON(userinfo).getString("headimgurl");
                }
                object.put("headimgurl", headimgurl);
            }
        }
        return object.toJSONString();
    }
}
