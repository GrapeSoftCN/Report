package interfaceApplication;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import Model.CommonModel;
import common.java.JGrapeSystem.rMsg;
import common.java.apps.appsProxy;
import common.java.authority.plvDef.UserMode;
import common.java.authority.plvDef.plvType;
import common.java.interfaceModel.GrapeDBSpecField;
import common.java.interfaceModel.GrapeTreeDBModel;
import common.java.json.JSONHelper;
import common.java.session.session;
import common.java.string.StringHelper;

/**
 * 举报拒绝/完结事由管理
 * 
 *
 */
public class Reason {
    private GrapeTreeDBModel reason;
    private GrapeDBSpecField gDbSpecField;
    private CommonModel model;
    private session se;
    private JSONObject userInfo = null;
    private String currentWeb = null;
    private Integer userType = 0;

    public Reason() {
        model = new CommonModel();

        reason = new GrapeTreeDBModel();
        gDbSpecField = new GrapeDBSpecField();
        gDbSpecField.importDescription(appsProxy.tableConfig("Reason"));
        reason.descriptionModel(gDbSpecField);
        reason.bindApp();
//        reason.enableCheck();//开启权限检查

        se = new session();
        userInfo = se.getDatas();
        if (userInfo != null && userInfo.size() != 0) {
            currentWeb = userInfo.getString("currentWeb"); // 当前站点id
            userType =userInfo.getInt("userType");//当前用户身份
        }
    }

    /**
     * 新增举报拒绝/完结事由
     * 
     * @param typeInfo
     * @return
     */
    @SuppressWarnings("unchecked")
    public String AddReson(String typeInfo) {
        Object info = null;
        JSONObject obj = null;
        String result = rMsg.netMSG(100, "新增举报拒绝/完结事由失败");
        typeInfo = CheckParam(typeInfo);
        if (typeInfo.contains("errorcode")) {
            return typeInfo;
        }
        JSONObject object = JSONObject.toJSON(typeInfo);
        JSONObject rMode = new JSONObject(plvType.chkType, plvType.powerVal).puts(plvType.chkVal, 100);//设置默认查询权限
    	JSONObject uMode = new JSONObject(plvType.chkType, plvType.powerVal).puts(plvType.chkVal, 200);
    	JSONObject dMode = new JSONObject(plvType.chkType, plvType.powerVal).puts(plvType.chkVal, 300);
    	object.put("rMode", rMode.toJSONString()); //添加默认查看权限
    	object.put("uMode", uMode.toJSONString()); //添加默认修改权限
    	object.put("dMode", dMode.toJSONString()); //添加默认删除权限
        if (object != null && object.size() > 0) {
            object.put("wbid", currentWeb);
            object.put("count", 0);
            info = reason.data(object).autoComplete().insertEx();
            obj = (info != null) ? findById(info.toString()) : new JSONObject();
        }
        return (obj != null && obj.size() > 0) ? rMsg.netMSG(0, obj) : result;
    }

    /**
     * 修改举报拒绝/完结事由
     * 
     * @param id
     * @param typeInfo
     * @return
     */
    public String UpdateReson(String id, String typeInfo) {
        boolean objects =false;
        String result = rMsg.netMSG(100, "修改失败");
        typeInfo = CheckParam(typeInfo);
        if (typeInfo.contains("errorcode")) {
            return typeInfo;
        }
        JSONObject object = JSONObject.toJSON(typeInfo);
        if (object != null && object.size() > 0) {
        	objects = reason.eq("_id", id).data(object).updateEx();
        }
        return result = objects  ? rMsg.netMSG(0, "修改成功") : result;
    }

    /**
     * 批量删除
     * @param ids
     * @return
     */
    public String DeleteReson(String ids) {
        long code = 0;
        String[] value = null;
        String result = rMsg.netMSG(100, "删除失败");
        if (StringHelper.InvaildString(ids)) {
            value = ids.split(",");
        }
        if (value != null) {
            reason.or();
            for (String tid : value) {
                reason.eq("_id", tid);
            }
            code = reason.deleteAll();
        }
        return code > 0 ? rMsg.netMSG(0, "删除成功") : result;
    }

    /**
     * 分页
     * 
     * @param ids
     * @param pageSize
     * @return
     */
    public String PageReson(int ids, int pageSize) {
        return PageByReson(ids, pageSize, null);
    }

    /**
     * 条件分页
     * 
     * @param ids
     * @param pageSize
     * @param info
     * @return
     */
    public String PageByReson(int ids, int pageSize, String info) {
        long total = 0;
        if (StringHelper.InvaildString(info)) {
            JSONArray condArray = model.buildCond(info);
            if (condArray != null && condArray.size() > 0) {
                reason.where(condArray);
            } else {
                return rMsg.netPAGE(ids, pageSize, total, new JSONArray());
            }
        }
//        判断当前用户身份：系统管理员，网站管理员
    	if (UserMode.root>userType && userType>= UserMode.admin) { //判断是否是网站管理员
    		reason.eq("wbid", currentWeb);
		}
        JSONArray array = reason.dirty().page(ids, pageSize);
        total = reason.count();
        return rMsg.netPAGE(ids, pageSize, total, (array != null && array.size() > 0) ? array : new JSONArray());
    }

    /**
     * 事由使用次数+1
     * @param name
     * @return
     */
    @SuppressWarnings("unchecked")
    public String addUseTime(String name) {
        int code = 99;
        String result = rMsg.netMSG(100, "操作失败");
        if (StringHelper.InvaildString(name)) {
            return rMsg.netMSG(3, "该拒绝/完成事由不存在");
        }
        reason.eq("Rcontent", name);
        JSONObject object = reason.dirty().find();
        if (object != null && object.size() > 0) {
            object.put("count", Integer.parseInt(object.getString("count")) + 1);
            code = reason.data(object).update() != null ? 0 : 99;
        }
        result = code == 0 ? rMsg.netMSG(0, "新增次数成功") : result;
        return result;
    }

    /**
     * 参数验证
     * 
     * @param typeInfo
     * @return
     */
    private String CheckParam(String typeInfo) {
        String typeName = "";
        if (!StringHelper.InvaildString(typeInfo)) {
            return rMsg.netMSG(1, "非法参数");
        }
        JSONObject object = JSONHelper.string2json(typeInfo);
        if (object != null && object.size() > 0) {
            if (object.containsKey("Rcontent")) {
                typeName = object.getString("Rcontent");
                if (findByName(typeName)) {
                    return rMsg.netMSG(2, "举报拒绝/完结事由已存在");
                }
            }
        }
        return typeInfo;
    }

    /**
     * 验证新添加的Reson是否存在
     * 
     * @param name
     * @return
     */
    private boolean findByName(String name) {
        JSONObject object = null;
        object = reason.eq("TypeName", "name").eq("wbid", currentWeb).find();
        return object != null && object.size() > 0;
    }

    /**
     * 类型查询显示
     * 
     * @param tid
     * @return
     */
    private JSONObject findById(String tid) {
        JSONObject object = null;
        if (StringHelper.InvaildString(tid)) {
            object = reason.eq("_id", "tid").find();
        }
        return object;
    }
}
