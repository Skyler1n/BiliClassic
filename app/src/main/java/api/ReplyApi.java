package tv.biliclassic.api;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import tv.biliclassic.model.Reply;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

public class ReplyApi {

    public static final int REPLY_TYPE_VIDEO_CHILD = 0;
    public static final int REPLY_TYPE_VIDEO = 1;
    public static final int REPLY_TYPE_ARTICLE = 12;
    public static final int REPLY_TYPE_DYNAMIC_CHILD = 11;
    public static final int REPLY_TYPE_DYNAMIC = 17;
    public static final String TOP_TIP = "[置顶]";

    public static class ReplyResult {
        public int code;
        public Reply reply;
    }

    /**
     * @param originId 评论区id，为评论所属内容的id，例如视频aid
     * @param rpid 父评论的id，无父评论则为0
     * @param pageNumber 分页，需要拉取的评论的页号
     * @param type 评论所属内容类型
     * @param sort 评论区排序方式，0=时间；1=点赞数量；2=回复数量
     * @param replyList 填充数组
     * @return -1错误,0正常，1到底了
     */
    public static int getReplies(long originId, long rpid, int pageNumber, int type, int sort, List replyList)
            throws JSONException, IOException {

        String url = "https://api.bilibili.com/x/v2/reply" + (rpid == 0 ? "" : "/reply")
                + "?pn=" + pageNumber + "&type=" + type + "&oid=" + originId
                + "&sort=" + sort + (rpid == 0 ? "" : ("&root=" + rpid));
        JSONObject all = NetWorkUtil.getJson(url);

        int size = replyList.size();
        if (all.getInt("code") == 0 && !all.isNull("data")) {
            JSONObject data = all.getJSONObject("data");
            JSONObject page = data.getJSONObject("page");
            if (!data.isNull("replies") && page.getInt("size") > 0) {
                if (rpid == 0 && data.has("top_replies") && page.getInt("num") == 1) {
                    analyzeReplyArray(true, data.getJSONArray("top_replies"), replyList);
                }
                JSONArray replies = data.getJSONArray("replies");
                analyzeReplyArray(rpid == 0, replies, replyList);
                if (replyList.size() == size) {
                    return 1;
                } else {
                    return 0;
                }
            } else {
                return 1;
            }
        } else {
            return -1;
        }
    }

    public static Reply getRootReply(int type, long originId, long rpid) {
        String url = "https://api.bilibili.com/x/v2/reply/reply" + "?type=" + type + "&oid=" + originId + "&root=" + rpid;
        try {
            JSONObject json = NetWorkUtil.getJson(url);
            if (json.getInt("code") != 0 || json.isNull("data")) {
                return null;
            }
            JSONObject data = json.getJSONObject("data");
            if (data.isNull("root")) {
                return null;
            }
            return new Reply(true, data.getJSONObject("root"));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static ReplyResult sendReply(long oid, long root, long parent, String text, int type)
            throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/reply/add";
        String arg = "oid=" + oid + "&type=" + type
                + (root == 0 ? "" : ("&root=" + root + "&parent=" + parent))
                + "&message=" + text + "&jsonp=jsonp&csrf=" + SharedPreferencesUtil.getString("csrf", "");
        String response = NetWorkUtil.post(url, arg, null);
        JSONObject result = new JSONObject(response);
        Log.e("debug-发送评论", result.toString());

        ReplyResult replyResult = new ReplyResult();
        replyResult.code = result.getInt("code");
        replyResult.reply = null;

        if (result.has("data") && !result.isNull("data")) {
            JSONObject data = result.getJSONObject("data");
            if (data.has("reply") && !data.isNull("reply")) {
                replyResult.reply = new Reply(root != 0, data.getJSONObject("reply"));
            }
        }
        return replyResult;
    }

    public static ReplyResult sendReply(long oid, long root, long parent, String text)
            throws IOException, JSONException {
        return sendReply(oid, root, parent, text, REPLY_TYPE_VIDEO);
    }

    public static ReplyResult sendDynamicReply(long oid, long root, long parent, String text)
            throws IOException, JSONException {
        return sendReply(oid, root, parent, text, REPLY_TYPE_DYNAMIC);
    }

    public static int likeReply(long oid, long rpid, boolean action)
            throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/reply/action";
        String arg = "oid=" + oid + "&type=1&rpid=" + rpid + "&action=" + (action ? "1" : "0")
                + "&jsonp=jsonp&csrf=" + SharedPreferencesUtil.getString("csrf", "");
        String response = NetWorkUtil.post(url, arg, null);
        JSONObject result = new JSONObject(response);
        Log.e("debug-点赞评论", result.toString());
        return result.getInt("code");
    }

    public static int deleteReply(long oid, long rpid, int type)
            throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/reply/del";
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData();
        formData.put("type", type);
        formData.put("oid", oid);
        formData.put("rpid", rpid);
        formData.put("csrf", SharedPreferencesUtil.getString("csrf", ""));
        String response = NetWorkUtil.post(url, formData.toString(), null);
        JSONObject result = new JSONObject(response);
        Log.e("debug-删除评论", result.toString());
        return result.getInt("code");
    }

    public static long getReplyCount(long oid, int type)
            throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/v2/reply/count?oid=" + oid + "&type=" + type;
        JSONObject all = NetWorkUtil.getJson(url);
        if (all.has("data") && !all.isNull("data")) {
            return all.getJSONObject("data").getLong("count");
        }
        return 0;
    }

    private static void analyzeReplyArray(boolean isRoot, JSONArray replies, List replyList)
            throws JSONException {
        for (int i = 0; i < replies.length(); i++) {
            JSONObject reply = replies.getJSONObject(i);
            Reply replyReturn = new Reply(isRoot, reply);
            replyList.add(replyReturn);
        }
    }
}