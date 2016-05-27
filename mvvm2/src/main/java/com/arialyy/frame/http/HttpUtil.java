package com.arialyy.frame.http;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.arialyy.frame.cache.CacheUtil;
import com.arialyy.frame.http.inf.IResponse;
import com.arialyy.frame.util.show.L;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by lyy on 2015/11/5.
 * 网络连接工具
 */
public class HttpUtil {
    private static final String TAG = "HttpUtil";
    private Context mContext = null;
    private OkHttpClient okHttpClient;
    private static volatile HttpUtil mUtil = null;
    private static final Object LOCK = new Object();
    private CacheUtil mCacheUtil;
    private Handler mHandler;
    private static final int TIME_OUT = 5000;
    public static final String CONTENT_TYPE_IMG = "image/*";
    public static final String CONTENT_TYPE_TEXT = "text/*";
    public static final String CONTENT_TYPE_FILE = "application/octet-stream";

    private HttpUtil() {
    }

    private HttpUtil(Context context) {
        mContext = context;
        mCacheUtil = new CacheUtil(mContext, false);
        mHandler = new Handler(Looper.getMainLooper());
        okHttpClient = new OkHttpClient.Builder().connectTimeout(TIME_OUT, TimeUnit.MILLISECONDS)
                .writeTimeout(TIME_OUT, TimeUnit.MILLISECONDS).readTimeout(300000, TimeUnit.MILLISECONDS).build();
    }

    public static HttpUtil getInstance(Context context) {
        if (mUtil == null) {
            synchronized (LOCK) {
                if (mUtil == null) {
                    mUtil = new HttpUtil(context);
                }
            }
        }
        return mUtil;
    }

    public void get(final @NonNull String url, @NonNull final IResponse absResponse) {
        get(url, null, absResponse, false);
    }

    public void get(final @NonNull String url, @NonNull final IResponse absResponse, boolean useCache) {
        get(url, null, absResponse, useCache);
    }

    public void get(final @NonNull String url, final Map<String, String> params,
                    @NonNull final IResponse absResponse) {
        get(url, params, absResponse, false);
    }

    public void post(final @NonNull String url, @NonNull final IResponse absResponse) {
        post(url, null, null, absResponse, false);
    }

    public void post(final @NonNull String url, final Map<String, String> params,
                     @NonNull final IResponse absResponse) {
        post(url, params, null, absResponse, false);
    }

    public void post(final @NonNull String url, final Map<String, String> params,
                     @NonNull final IResponse absResponse, final boolean useCache) {
        post(url, params, null, absResponse, useCache);
    }

    public void post(final @NonNull String url, final Map<String, String> params,
                     final Map<String, String> header, @NonNull final IResponse absResponse) {
        post(url, params, header, absResponse, false);
    }

    /**
     * 上传文件
     *
     * @param key 上传文件键值
     */
    public void uploadFile(@NonNull final String url, @NonNull final String filePath, @NonNull final String key,
                           final String contentType, final Map<String, String> header, @NonNull final IResponse absResponse) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                File file = new File(filePath);
                String BOUNDARY = UUID.randomUUID().toString(); // 边界标识 随机生成
                String PREFIX = "--", LINE_END = "\r\n";
                String CONTENT_TYPE = "multipart/form-data"; // 内容类型
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setReadTimeout(5000);
                    conn.setConnectTimeout(5000);
                    conn.setDoInput(true);
                    conn.setDoOutput(true);
                    conn.setUseCaches(false);
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Charset", "utf-8"); // 设置编码
                    conn.setRequestProperty("connection", "keep-alive");
                    conn.setRequestProperty("Content-Type", CONTENT_TYPE + ";boundary=" + BOUNDARY);

                    if (header != null && header.size() > 0) {
                        Set set = header.entrySet();
                        for (Object aSet : set) {
                            Map.Entry entry = (Map.Entry) aSet;
                            conn.setRequestProperty(entry.getKey() + "", entry.getValue() + "");
                        }
                    }
                    OutputStream outputSteam = conn.getOutputStream();
                    DataOutputStream dos = new DataOutputStream(outputSteam);
                    StringBuilder sb = new StringBuilder();
                    sb.append(PREFIX);
                    sb.append(BOUNDARY);
                    sb.append(LINE_END);
                    sb.append("Content-Disposition: form-data; name=\"").append(key).append("\"; filename=\"").append(file.getName()).append("\"").append(LINE_END);
                    sb.append("Content-Type:").append(contentType).append("; charset=utf-8").append(LINE_END);
                    sb.append(LINE_END);
                    dos.write(sb.toString().getBytes());
                    InputStream is = new FileInputStream(file);
                    byte[] bytes = new byte[1024];
                    int len = 0;
                    while ((len = is.read(bytes)) != -1) {
                        dos.write(bytes, 0, len);
                    }
                    is.close();
                    dos.write(LINE_END.getBytes());
                    byte[] end_data = (PREFIX + BOUNDARY + PREFIX + LINE_END).getBytes();
                    dos.write(end_data);
                    dos.flush();
                    int res = conn.getResponseCode();
                    if (res == 200) {
                        BufferedInputStream inputStream = new BufferedInputStream(conn.getInputStream());
                        byte[] buf = new byte[1024];
                        StringBuilder stringBuilder = new StringBuilder();
                        while (inputStream.read(buf) > 0) {
                            stringBuilder.append(new String(buf, 0, buf.length));
                        }
                        String data = stringBuilder.toString();
                        L.j(data);
                        absResponse.onResponse(data);
                    } else {
                        absResponse.onError("error");
                    }
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }


    /**
     * 基本get方法
     */
    public void get(final @NonNull String url, final Map<String, String> params,
                    @NonNull final IResponse absResponse, final boolean useCache) {
        L.v(TAG, "请求链接 >>>> " + url);
        String requestUrl = url;
        if (params != null && params.size() > 0) {
            Set set = params.entrySet();
            int i = 0;
            requestUrl += "?";
            for (Object aSet : set) {
                i++;
                Map.Entry entry = (Map.Entry) aSet;
                requestUrl += entry.getKey() + "=" + entry.getValue() + (i < params.size() ? "&" : "");
            }
            L.v(TAG, "请求参数为 >>>> ");
            L.m(params);
        }
        final Request request = new Request.Builder().url(requestUrl).build();
        Call call = okHttpClient.newCall(request);
        //请求加入调度
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                L.e(TAG, "请求链接【" + url + "】失败");
                String data = null;
                if (useCache) {
                    data = mCacheUtil.getStringCache(url + L.m2s(params));
                    L.d(TAG, "数据获取成功，获取到的数据为 >>>> ");
                    L.j(data);
                }
                if (TextUtils.isEmpty(data)) {
                    setOnError(request, absResponse);
                } else {
                    setOnResponse(data, absResponse);
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String data = response.body().string();
                L.d(TAG, "数据获取成功，获取到的数据为 >>>> ");
                L.j(data);
                if (useCache) {
                    L.v(TAG, "缓存链接【" + url + "】的数据");
                    mCacheUtil.putStringCache(url + L.m2s(params), data);
                }
                setOnResponse(data, absResponse);
            }
        });
    }

    /**
     * 基本的Post方法
     */
    public void post(final @NonNull String url, final Map<String, String> params,
                     final Map<String, String> header, @NonNull final IResponse absResponse,
                     final boolean useCache) {
        L.v(TAG, "请求链接 >>>> " + url);
        FormBody.Builder formB = new FormBody.Builder();
        //头数据
        Headers.Builder hb = new Headers.Builder();
        if (header != null && header.size() > 0) {
            Set set = header.entrySet();
            for (Object aSet : set) {
                Map.Entry entry = (Map.Entry) aSet;
                hb.add(entry.getKey() + "", entry.getValue() + "");
            }
            L.v(TAG, "请求的头数据为 >>>> ");
            L.m(header);
        }
        //请求参数
        if (params != null && params.size() > 0) {
            Set set = params.entrySet();
            for (Object aSet : set) {
                Map.Entry entry = (Map.Entry) aSet;
                formB.add(entry.getKey() + "", entry.getValue() + "");
            }
            L.v(TAG, "请求参数为 >>>> ");
            L.m(params);
        } else {
            formB.add("null", "null");
        }

        Request request = new Request.Builder().url(url).post(formB.build()).headers(hb.build()).build();
        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                L.e(TAG, "请求链接【" + url + "】失败");
                String data = null;
                if (useCache) {
                    data = mCacheUtil.getStringCache(url + L.m2s(params));
                    L.d(TAG, "从缓存读取的数据为 >>>> ");
                    L.j(data);
                }
                if (TextUtils.isEmpty(data)) {
                    setOnError(call, absResponse);
                } else {
                    setOnResponse(data, absResponse);
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String data = response.body().string();
                L.d(TAG, "数据获取成功，获取到的数据为 >>>>");
                L.j(data);
                if (useCache) {
                    L.v(TAG, "缓存链接【" + url + "】的数据");
                    mCacheUtil.putStringCache(url + L.m2s(params), data);
                }
                setOnResponse(data, absResponse);
            }

        });
    }

    private void setOnError(final Object error, final IResponse response) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                response.onError(error);
            }
        });
    }

    private void setOnResponse(final String data, final IResponse response) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                response.onResponse(data);
            }
        });
    }

    /**
     * 返回String类型的响应
     */
    public static class AbsResponse implements IResponse {

        @Override
        public void onResponse(String data) {

        }

        @Override
        public void onError(Object error) {

        }
    }

}
