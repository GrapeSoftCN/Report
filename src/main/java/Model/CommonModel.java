package Model;

import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import apps.appsProxy;
import database.DBHelper;
import database.db;
import database.dbFilter;
import httpClient.request;
import nlogger.nlogger;
import security.codec;
import string.StringHelper;
import thirdsdk.wechatHelper;

public class CommonModel {
	private String appid = appsProxy.appidString();

	/**
	 * 微信帮助类
	 * 
	 * @param sdkUserID
	 * @return
	 */
	public wechatHelper getWeChatHelper(int sdkUserID) {
		wechatHelper wechatHelper = null;
		if (sdkUserID != 0) {
			String _appid = getwechatAppid(sdkUserID, "appid");
			String _appsecret = getwechatAppid(sdkUserID, "appsecret");
			if (StringHelper.InvaildString(_appid) && StringHelper.InvaildString(_appsecret)) {
				wechatHelper = new wechatHelper(_appid, _appsecret);
			}
		}
		return wechatHelper;
	}

	/**
	 * 获取appid，appsecret
	 * 
	 * @param id
	 * @param key
	 * @return
	 */
	public String getwechatAppid(int id, String key) {
		DBHelper helper = new DBHelper("localdb", "sdkuser");
		db db = helper.bind(appid);
		JSONObject object = db.eq("id", id).field("configString").find();
		String value = "";
		if (object != null && object.size() > 0) {
			if (object.containsKey("configstring")) {
				object = JSONObject.toJSON(object.getString("configstring"));
				if (object != null && object.size() > 0) {
					if (object.containsKey(key)) {
						value = object.getString(key);
					}
				}
			}
		}
		return value;
	}
    /**
     * 发送数据到kafka
     * 
     * @param id
     * @param mode
     * @param newstate
     */
    public void setKafka(String id, int mode, int newstate) {
        String APIHost = getconfig("APIHost");
        String APIAppid = getconfig("appid");
        if (!APIHost.equals("") && !APIAppid.equals("")) {
            request.Get(APIHost + "/" + APIAppid + "/sendServer/ShowInfo/getKafkaData/" + id + "/" + appsProxy.appidString() + "/int:1/int:" + mode + "/int:" + newstate);
        }
    }

    /**
     * 获取配置信息
     * 
     * @param key
     * @return
     */
    private String getconfig(String key) {
        String value = "";
        try {
            JSONObject object = JSONObject.toJSON(appsProxy.configValue().getString("other"));
            if (object != null && object.size() > 0) {
                value = object.getString(key);
            }
        } catch (Exception e) {
            nlogger.logout(e);
            value = "";
        }
        return value;
    }

    /**
     * 获取微服务配置中的other内容
     * 
     * @param key
     * @return 原格式 {"cache":"redis","other":{"filepath":""},"db":"mongodb"}
     */
    public String getConfigString(String key) {
        String value = "";
        JSONObject object = appsProxy.configValue();
        if (object != null && object.size() > 0) {
            object = JSONObject.toJSON(object.getString("other"));
            if (object != null && object.size() > 0) {
                value = object.getString(key);
            }
        }
        return value;
    }

    /**
     * 获取文件访问地址
     * 
     * @param imageURL
     * @return
     */
    public String getImageUri(String imageURL) {
        int i = 0;
        String dir = getConfigString("weburl");
        if (StringHelper.InvaildString(imageURL)) {
            if (imageURL.contains("File//upload")) {
                i = imageURL.toLowerCase().indexOf("file//upload");
                imageURL = "\\" + imageURL.substring(i);
            }
            if (imageURL.contains("File\\upload")) {
                i = imageURL.toLowerCase().indexOf("file\\upload");
                imageURL = "\\" + imageURL.substring(i);
            }
            if (imageURL.contains("File/upload")) {
                i = imageURL.toLowerCase().indexOf("file/upload");
                imageURL = "\\" + imageURL.substring(i);
            }
            imageURL = dir + imageURL;
        }
        return imageURL;
    }

    /**
     * 整合参数，将JSONObject类型的参数封装成JSONArray类型
     * 
     * @param object
     * @return
     */
    public JSONArray buildCond(String Info) {
        String key;
        Object value;
        JSONArray condArray = null;
        JSONObject object = JSONObject.toJSON(Info);
        dbFilter filter = new dbFilter();
        if (object != null && object.size() > 0) {
            for (Object object2 : object.keySet()) {
                key = object2.toString();
                value = object.get(key);
                filter.eq(key, value);
            }
            condArray = filter.build();
        } else {
            condArray = JSONArray.toJSONArray(Info);
        }
        return condArray;
    }

    /**
     * 举报件内容解码
     * 
     * @param array
     * @return
     */
    @SuppressWarnings("unchecked")
    public JSONArray dencode(JSONArray array) {
        try {
            if (array == null || array.size() == 0) {
                return new JSONArray();
            }
            for (int i = 0; i < array.size(); i++) {
                JSONObject object = (JSONObject) array.get(i);
                array.set(i, dencode(object));
            }
        } catch (Exception e) {
            nlogger.logout(e);
            array = new JSONArray();
        }
        return array;
    }

    /**
     * 举报件内容解码
     * 
     * @param obj
     * @return
     */
    @SuppressWarnings("unchecked")
    public JSONObject dencode(JSONObject obj) {
        String temp;
        String[] fields = { "content", "reason" };
        if (obj != null && obj.size() > 0) {
            for (String field : fields) {
                if (obj.containsKey(field)) {
                    temp = obj.getString(field);
                    if (StringHelper.InvaildString(temp)) {
                        temp = codec.DecodeHtmlTag(temp);
                        temp = codec.decodebase64(temp);
                        obj.put(field, temp);
                    }
                }
            }
        }
        return obj;
    }

    /**
     * 获取文件详细信息
     * 
     * @param array
     * @return
     */
    @SuppressWarnings("unchecked")
    public JSONArray getImage(JSONArray array) {
        String fid = "";
        JSONObject object;
        if (array == null || array.size() <= 0) {
            return new JSONArray();
        }
        fid = getFid(array); // 获取文件id
        JSONObject obj = getFileInfo(fid);
        if (obj != null && obj.size() > 0) {
            for (int i = 0; i < array.size(); i++) {
                object = (JSONObject) array.get(i);
                array.set(i, FillFileInfo(obj, object));
            }
        }
        return array;
    }

    public JSONObject getImage(JSONObject object) {
        // 获取fid
        String fid = getFid(object);
        JSONObject obj = getFileInfo(fid); // 获取文件信息
        if (obj != null && obj.size() > 0) {
            object = FillFileInfo(obj, object);
        }
        return object;
    }

    @SuppressWarnings("unchecked")
    private JSONObject FillFileInfo(JSONObject FileObj,JSONObject object) {
        String attrlist = "", filetype = "";
        String[] attr;
        JSONObject FileInfoObj;
        List<String> imgList = new ArrayList<String>();
        List<String> videoList = new ArrayList<String>();
        List<String> fileList = new ArrayList<String>();
        attr = object.getString("attr").split(",");
        int attrlength = attr.length;
        if (attrlength != 0 && !attr[0].equals("") || attrlength > 1) {
            for (int j = 0; j < attrlength; j++) {
                FileInfoObj = (JSONObject) FileObj.get(attr[j]);
                if (FileInfoObj != null && FileInfoObj.size() != 0) {
                    attrlist = FileInfoObj.get("filepath").toString();
                    if (FileInfoObj.containsKey("filetype")) {
                        filetype = FileInfoObj.getString("filetype");
                    }
                    object.put("attrFile" + j, FileInfoObj);
                    if ("1".equals(filetype)) {
                        imgList.add(attrlist);  //视频
                    }else if ("2".equals(filetype)) {
                        videoList.add(attrlist);
                    }else {
                        fileList.add(attrlist);
                    }
                }
            }
        }
        object.put("image", imgList.size() != 0 ? StringHelper.join(imgList) : "");
        object.put("video", videoList.size() != 0 ? StringHelper.join(videoList) : "");
        object.put("file", fileList.size() != 0 ? StringHelper.join(fileList) : "");
        return object;
    }


    /**
     * 获取文件id
     * 
     * @param array
     * @return
     */
    private String getFid(JSONArray array) {
        String fid = "", temp;
        JSONObject tempObj;
        if (array != null && array.size() > 0) {
            for (Object object : array) {
                tempObj = (JSONObject) object;
                if (tempObj != null && tempObj.size() > 0) {
                    temp = getFid(tempObj);
                    if (StringHelper.InvaildString(temp)) {
                        fid += temp + ",";
                    }
                }
            }
        }
        return StringHelper.fixString(fid, ',');
    }

    /**
     * 获取文件id
     * 
     * @param object
     * @return
     */
    private String getFid(JSONObject object) {
        String fid = "";
        if (object != null && object.size() > 0) {
            if (object.containsKey("attr")) {
                fid = object.getString("attr");
            }
        }
        return fid;
    }

    /**
     * 获取文件信息
     * @param fid
     * @return
     */
    private JSONObject getFileInfo(String fid) {
        String temp = "";
        if (StringHelper.InvaildString(fid)) {
            temp = appsProxy.proxyCall("/GrapeFile/Files/getFileByID/" + fid).toString();
        }
        return JSONObject.toJSON(temp);
    }
}
