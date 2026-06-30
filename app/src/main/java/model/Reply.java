package tv.biliclassic.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

public class Reply implements Serializable {

    public long rpid;
    public long oid;
    public long root;
    public long parent;
    public boolean forceDelete;
    public String ofBvid = "";
    public String pubTime;
    public UserInfo sender;
    public String message;
    public ArrayList pictureList = new ArrayList();
    public int likeCount;
    public boolean upLiked;
    public boolean upReplied;
    public boolean liked;
    public int childCount;
    public boolean isDynamic;
    public ArrayList childMsgList = new ArrayList();
    public boolean isTop;

    public Reply() {
    }

    // 解析评论 JSON
    public Reply(boolean isRoot, JSONObject replyJson) throws JSONException {
        this.rpid = replyJson.getLong("rpid");
        this.oid = replyJson.getLong("oid");
        this.root = replyJson.getLong("root");
        this.parent = replyJson.getLong("parent");

        if (replyJson.has("member") && !replyJson.isNull("member")) {
            this.sender = new UserInfo(replyJson.getJSONObject("member"));
        }

        JSONObject content = replyJson.getJSONObject("content");
        JSONObject replyCtrl = replyJson.getJSONObject("reply_control");
        long ctime = replyJson.getLong("ctime") * 1000;

        // 处理时间
        String time;
        if (System.currentTimeMillis() - ctime < 3 * 24 * 60 * 60 * 1000 && replyCtrl.has("time_desc")) {
            time = replyCtrl.getString("time_desc");
        } else {
            time = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(ctime);
        }

        // IP 属地
        if (replyCtrl.has("location")) {
            String location = replyCtrl.getString("location");
            if (location != null && location.length() > 5) {
                time = time + " | IP:" + location.substring(5);
            }
        }
        this.pubTime = time;

        // 置顶标记
        if (replyCtrl.has("is_up_top") && replyCtrl.getBoolean("is_up_top")) {
            this.isTop = true;
        }

        // 消息内容（去掉 HTML 标签）
        String rawMessage = content.getString("message");
        this.message = stripHtml(rawMessage);
        if (this.isTop) {
            this.message = "[置顶]" + this.message;
        }

        this.likeCount = replyJson.getInt("like");
        this.liked = replyJson.getInt("action") == 1;

        // UP主操作状态
        if (replyJson.has("up_action") && !replyJson.isNull("up_action")) {
            JSONObject upAction = replyJson.getJSONObject("up_action");
            this.upLiked = upAction.optBoolean("like", false);
            this.upReplied = upAction.optBoolean("reply", false);
        }

        // 根评论额外信息
        if (isRoot) {
            // 图片列表
            if (content.has("pictures") && !content.isNull("pictures")) {
                JSONArray pictures = content.getJSONArray("pictures");
                for (int j = 0; j < pictures.length(); j++) {
                    JSONObject picture = pictures.getJSONObject(j);
                    String imgSrc = picture.optString("img_src", "");
                    if (imgSrc != null && imgSrc.length() > 0) {
                        this.pictureList.add(imgSrc);
                    }
                }
            }

            this.childCount = replyJson.getInt("rcount");

            // 子评论（楼中楼）
            if (replyJson.has("replies") && !replyJson.isNull("replies")) {
                JSONArray childReplies = replyJson.getJSONArray("replies");
                for (int j = 0; j < childReplies.length(); j++) {
                    Reply childReply = new Reply(false, childReplies.getJSONObject(j));
                    this.childMsgList.add(childReply);
                }
            }
        }
    }

    // 简单去除 HTML 标签
    private String stripHtml(String html) {
        if (html == null || html.length() == 0) {
            return "";
        }
        // 去掉 <br> 标签
        String result = html.replaceAll("<br\\s*/?>", "\n");
        // 去掉其他 HTML 标签
        result = result.replaceAll("<[^>]+>", "");
        return result;
    }

    public String getFormattedTime() {
        return pubTime != null ? pubTime : "";
    }

    public boolean isRoot() {
        return root == 0;
    }

    public boolean isTop() {
        return isTop;
    }

    public String getContent() {
        return message != null ? message : "";
    }
}