package interfaceApplication;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import JGrapeSystem.rMsg;
import Model.CommonModel;
import apps.appsProxy;
import interfaceModel.GrapeDBSpecField;
import interfaceModel.GrapeTreeDBModel;
import json.JSONHelper;
import session.session;
import string.StringHelper;

/**
 * 举报类型管理
 * 
 *
 */
public class Rtype {
    private GrapeTreeDBModel rType;
    private GrapeDBSpecField gDbSpecField;
    private CommonModel model;
    private session se;
    private JSONObject userInfo = null;
    private String currentWeb = null;

    public Rtype() {
        model = new CommonModel();

        rType = new GrapeTreeDBModel();
        gDbSpecField = new GrapeDBSpecField();
        gDbSpecField.importDescription(appsProxy.tableConfig("Rtype"));
        rType.descriptionModel(gDbSpecField);
        rType.bindApp();

        se = new session();
        userInfo = se.getDatas();
        if (userInfo != null && userInfo.size() != 0) {
            currentWeb = userInfo.getString("currentWeb"); // 当前站点id
        }
    }

    /**
     * 新增举报类型
     * 
     * @param typeInfo
     * @return
     */
    @SuppressWarnings("unchecked")
    public String AddType(String typeInfo) {
        Object info = null;
        JSONObject obj = null;
        String result = rMsg.netMSG(100, "新增举报类型失败");
        typeInfo = CheckParam(typeInfo);
        if (typeInfo.contains("errorcode")) {
            return typeInfo;
        }
        JSONObject object = JSONObject.toJSON(typeInfo);
        if (object != null && object.size() > 0) {
            object.put("wbid", currentWeb);
            info = rType.data(object).insertOnce();
            obj = (info != null) ? findById(info.toString()) : new JSONObject();
        }
        return (obj != null && obj.size() > 0) ? rMsg.netMSG(0, obj) : result;
    }

    /**
     * 修改举报类型
     * 
     * @param id
     * @param typeInfo
     * @return
     */
    public String UpdateType(String id, String typeInfo) {
        int code = 99;
        String result = rMsg.netMSG(100, "修改失败");
        typeInfo = CheckParam(typeInfo);
        if (typeInfo.contains("errorcode")) {
            return typeInfo;
        }
        JSONObject object = JSONObject.toJSON(typeInfo);
        if (object != null && object.size() > 0) {
            code = rType.eq("_id", id).data(object).update() != null ? 0 : 99;
        }
        return code == 0 ? rMsg.netMSG(0, "修改成功") : result;
    }

    /**
     * 删除
     * 
     * @param id
     * @return
     */
    public String DeleteType(String id) {
        return DeleteBatchType(id);
    }

    // 批量删除
    public String DeleteBatchType(String ids) {
        long code = 0;
        String[] value = null;
        String result = rMsg.netMSG(100, "删除失败");
        if (!StringHelper.InvaildString(ids)) {
            value = ids.split(",");
        }
        if (value != null) {
            rType.or();
            for (String tid : value) {
                rType.eq("_id", tid);
            }
            code = rType.deleteAll();
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
    public String PageType(int ids, int pageSize) {
        return search(ids, pageSize, null);
    }

    /**
     * 条件分页
     * 
     * @param ids
     * @param pageSize
     * @param info
     * @return
     */
    public String search(int ids, int pageSize, String info) {
        long total = 0;
        if (!StringHelper.InvaildString(info)) {
            JSONArray condArray = model.buildCond(info);
            if (condArray != null && condArray.size() > 0) {
                rType.where(condArray);
            } else {
                return rMsg.netPAGE(ids, pageSize, total, new JSONArray());
            }
        }
        rType.eq("wbid", currentWeb);
        JSONArray array = rType.dirty().page(ids, pageSize);
        total = rType.count();
        return rMsg.netPAGE(ids, pageSize, total, (array != null && array.size() > 0) ? array : new JSONArray());
    }

    /**
     * 举报类型查询，封装成{typeId:typeName,...}
     * 
     * @param tid
     * @return
     */
    @SuppressWarnings("unchecked")
    protected JSONObject FindByIds(String tids) {
        String[] value = null;
        JSONArray array = null;
        JSONObject rObject = new JSONObject(), object;
        String id, typeName;
        if (!StringHelper.InvaildString(tids)) {
            value = tids.split(",");
        }
        if (value != null) {
            rType.or();
            for (String tid : value) {
                rType.eq("_id", tid);
            }
            array = rType.and().eq("wbid", currentWeb).select();
        }
        if (array != null && array.size() > 0) {
            for (Object obj : array) {
                object = (JSONObject) obj;
                id = object.getMongoID("_id");
                typeName = object.getString("TypeName");
                rObject.put(id, typeName);
            }
        }
        return rObject;
    }

    /**
     * 参数验证
     * 
     * @param typeInfo
     * @return
     */
    private String CheckParam(String typeInfo) {
        String typeName = "";
        if (StringHelper.InvaildString(typeInfo)) {
            return rMsg.netMSG(1, "参数不能为空");
        }
        JSONObject object = JSONHelper.string2json(typeInfo);
        if (object != null && object.size() > 0) {
            if (object.containsKey("TypeName")) {
                typeName = object.getString("TypeName");
                if (findByName(typeName)) {
                    return rMsg.netMSG(2, "该举报类型已存在");
                }
            }
        }
        return typeInfo;
    }

    /**
     * 验证新添加的举报类型是否存在
     * 
     * @param name
     * @return
     */
    private boolean findByName(String name) {
        JSONObject object = null;
        object = rType.eq("TypeName", "name").eq("wbid", currentWeb).find();
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
        if (!StringHelper.InvaildString(tid)) {
            object = rType.eq("_id", "tid").find();
        }
        return object;
    }
}
