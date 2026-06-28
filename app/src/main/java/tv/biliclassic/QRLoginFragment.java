package tv.biliclassic;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.support.v4.app.Fragment;

import org.json.JSONObject;

import java.util.Timer;
import java.util.TimerTask;

import tv.biliclassic.api.LoginApi;
import tv.biliclassic.util.MsgUtil;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

public class QRLoginFragment extends Fragment {

    private static final String TAG = "QRLoginFragment";
    private ImageView qrImageView;
    private TextView scanStat;
    private Timer timer;
    private boolean needRefresh = false;
    private boolean fromSetup = false;
    private Handler mainHandler;
    private boolean isDestroyed = false;

    public QRLoginFragment() {
    }

    public static QRLoginFragment newInstance(boolean fromSetup) {
        Bundle args = new Bundle();
        args.putBoolean("from_setup", fromSetup);
        QRLoginFragment fragment = new QRLoginFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler(Looper.getMainLooper());

        Bundle bundle = getArguments();
        if (bundle != null) {
            fromSetup = bundle.getBoolean("from_setup", false);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_qr_login, container, false);

        qrImageView = (ImageView) view.findViewById(R.id.qrImage);
        scanStat = (TextView) view.findViewById(R.id.scanStat);
        Button btnBack = (Button) view.findViewById(R.id.btn_back);
        Button btnManualLogin = (Button) view.findViewById(R.id.btn_manual_login);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (timer != null) {
                    timer.cancel();
                }
                if (getActivity() != null) {
                    getActivity().finish();
                }
            }
        });

        btnManualLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), SpecialLoginActivity.class);
                intent.putExtra("login", true);
                startActivity(intent);
                if (timer != null) {
                    timer.cancel();
                }
                if (getActivity() != null) {
                    getActivity().finish();
                }
            }
        });

        qrImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (needRefresh) {
                    refreshQrCode();
                }
            }
        });

        refreshQrCode();
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    @Override
    public void onDestroy() {
        isDestroyed = true;
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        super.onDestroy();
    }

    private void refreshQrCode() {
        needRefresh = false;
        if (qrImageView == null) return;
        qrImageView.setEnabled(false);
        if (scanStat != null) {
            scanStat.setText("正在获取二维码...");
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Bitmap qrImage = LoginApi.getLoginQR();
                    if (isDestroyed || getActivity() == null) return;

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isDestroyed || getActivity() == null) return;
                            if (qrImage != null && qrImageView != null) {
                                qrImageView.setImageBitmap(qrImage);
                                qrImageView.setEnabled(true);
                                if (scanStat != null) {
                                    scanStat.setText("请使用B站APP扫码登录\n点击二维码可以刷新");
                                }
                                needRefresh = true;
                                startLoginDetect();
                            } else {
                                if (scanStat != null) {
                                    scanStat.setText("生成二维码失败，请重试");
                                }
                                if (qrImageView != null) {
                                    qrImageView.setEnabled(true);
                                }
                                needRefresh = true;
                            }
                        }
                    });
                } catch (final Exception e) {
                    if (isDestroyed || getActivity() == null) return;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isDestroyed || getActivity() == null) return;
                            if (scanStat != null) {
                                scanStat.setText("获取二维码失败：" + e.getMessage());
                            }
                            if (qrImageView != null) {
                                qrImageView.setEnabled(true);
                            }
                            needRefresh = true;
                        }
                    });
                }
            }
        }).start();
    }

    private void startLoginDetect() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!isAdded() || isDestroyed || getActivity() == null) {
                    this.cancel();
                    return;
                }
                try {
                    String response = LoginApi.getLoginState();
                    if (response == null || response.length() == 0) {
                        return;
                    }

                    JSONObject json = new JSONObject(response);
                    int apiCode = json.optInt("code", -1);
                    if (apiCode != 0) {
                        return;
                    }

                    JSONObject data = json.getJSONObject("data");
                    int scanCode = data.optInt("code", -1);

                    if (scanCode == 0) {
                        this.cancel();
                        if (timer != null) timer = null;

                        final String crossUrl = data.optString("url", "");
                        if (crossUrl != null && crossUrl.length() > 0) {
                            try {
                                NetWorkUtil.get(crossUrl);
                                Log.e(TAG, "跨域请求成功");
                                saveUserInfoFromUrl(crossUrl);
                            } catch (Exception e) {
                                Log.e(TAG, "请求跨域 URL 失败: " + e.getMessage());
                            }
                        }

                        if (!isDestroyed && getActivity() != null) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (isDestroyed || getActivity() == null) return;
                                    MsgUtil.showMsg(getActivity(), "登录成功");
                                    if (getActivity() != null) {
                                        getActivity().setResult(LoginActivity.RESULT_OK);
                                        getActivity().finish();
                                    }
                                }
                            });
                        }

                    } else if (scanCode == 86090) {
                        if (!isDestroyed && getActivity() != null) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (isDestroyed || getActivity() == null) return;
                                    if (scanStat != null) {
                                        scanStat.setText("已扫描，请在手机上点击确认登录");
                                    }
                                }
                            });
                        }
                    } else if (scanCode == 86101) {
                        if (!isDestroyed && getActivity() != null) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (isDestroyed || getActivity() == null) return;
                                    if (scanStat != null) {
                                        scanStat.setText("请使用B站APP扫码登录");
                                    }
                                }
                            });
                        }
                    } else if (scanCode == 86038) {
                        this.cancel();
                        if (timer != null) timer = null;
                        if (!isDestroyed && getActivity() != null) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (isDestroyed || getActivity() == null) return;
                                    if (scanStat != null) {
                                        scanStat.setText("二维码已过期，点击二维码刷新");
                                    }
                                    needRefresh = true;
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    this.cancel();
                    if (timer != null) timer = null;
                }
            }
        }, 1000, 1000);
    }

    private void saveUserInfoFromUrl(String url) {
        String dedeUserID = extractQueryParam(url, "DedeUserID");
        String sessData = extractQueryParam(url, "SESSDATA");
        String biliJct = extractQueryParam(url, "bili_jct");

        if (dedeUserID != null && dedeUserID.length() > 0) {
            try {
                SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, Long.parseLong(dedeUserID));
            } catch (NumberFormatException e) {
                Log.e(TAG, "解析 DedeUserID 失败: " + e.getMessage());
            }

            if (biliJct != null && biliJct.length() > 0) {
                SharedPreferencesUtil.putString("csrf", biliJct);
                Log.e(TAG, "保存 csrf 成功: " + biliJct);
            }

            String manualCookies = "DedeUserID=" + dedeUserID + "; SESSDATA=" + sessData + "; bili_jct=" + biliJct;
            SharedPreferencesUtil.putString("cookies", manualCookies);
            NetWorkUtil.setCookieString(manualCookies);
            NetWorkUtil.refreshHeaders();

            Log.e(TAG, "保存用户信息成功，mid: " + dedeUserID);
            fetchUserNameWithRetry(0);
        } else {
            Log.e(TAG, "从 URL 解析用户信息失败");
        }
    }

    private void fetchUserNameWithRetry(final int retryCount) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (isDestroyed) return;
                try {
                    if (retryCount > 0) {
                        Thread.sleep(2000);
                    }

                    String cookies = NetWorkUtil.getCookieString();
                    Log.e(TAG, "fetchUserName - 当前Cookie长度: " + (cookies == null ? 0 : cookies.length()));

                    String response = NetWorkUtil.get("https://api.bilibili.com/x/web-interface/nav");
                    if (response == null) return;

                    Log.e(TAG, "nav 响应长度: " + response.length());

                    JSONObject json = new JSONObject(response);
                    int code = json.optInt("code", -1);

                    if (code == 0) {
                        JSONObject data = json.getJSONObject("data");
                        final String uname = data.getString("uname");
                        final long mid = data.getLong("mid");

                        SharedPreferencesUtil.putString("uname", uname);
                        Log.e(TAG, "获取用户名成功: " + uname);
                    } else if (retryCount < 3) {
                        Log.e(TAG, "nav 返回错误码: " + code + "，重试 " + (retryCount + 1));
                        fetchUserNameWithRetry(retryCount + 1);
                    } else {
                        Log.e(TAG, "nav 返回错误码: " + code + "，已达最大重试次数");
                    }
                } catch (Exception e) {
                    if (retryCount < 3) {
                        Log.e(TAG, "获取用户名异常: " + e.getMessage() + "，重试 " + (retryCount + 1));
                        fetchUserNameWithRetry(retryCount + 1);
                    } else {
                        Log.e(TAG, "获取用户名失败: " + e.getMessage());
                    }
                }
            }
        }).start();
    }

    private String extractQueryParam(String url, String paramName) {
        try {
            if (url == null || url.length() == 0) return null;
            String[] parts = url.split("\\?");
            if (parts.length < 2) return null;
            String[] params = parts[1].split("&");
            for (String param : params) {
                String[] kv = param.split("=");
                if (kv.length == 2 && kv[0].equals(paramName)) {
                    return kv[1];
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "解析参数失败: " + e.getMessage());
        }
        return null;
    }
}