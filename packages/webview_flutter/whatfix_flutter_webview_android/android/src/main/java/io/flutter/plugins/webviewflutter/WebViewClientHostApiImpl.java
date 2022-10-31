// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.webviewflutter;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.webkit.WebResourceErrorCompat;
import androidx.webkit.WebViewClientCompat;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Host api implementation for {@link WebViewClient}.
 *
 * <p>Handles creating {@link WebViewClient}s that intercommunicate with a paired Dart object.
 */
public class WebViewClientHostApiImpl implements GeneratedAndroidWebView.WebViewClientHostApi {
  private final InstanceManager instanceManager;
  private final WebViewClientCreator webViewClientCreator;
  private final WebViewClientFlutterApiImpl flutterApi;

  /**
   * An interface implemented by a class that extends {@link WebViewClient} and {@link Releasable}.
   */
  public interface ReleasableWebViewClient extends Releasable {}

  /** Implementation of {@link WebViewClient} that passes arguments of callback methods to Dart. */
  @RequiresApi(Build.VERSION_CODES.N)
  public static class WebViewClientImpl extends WebViewClient implements ReleasableWebViewClient {
    @Nullable private WebViewClientFlutterApiImpl flutterApi;
    private final boolean shouldOverrideUrlLoading;
    private final Map<String, String> fileToUrlMap;

    @Nullable
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
      System.out.println("WebViewClientImpl: shouldInterceptRequest()");
      String requestUrl = null;
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
        requestUrl = request.getUrl().toString();
      }

      System.out.println("WebViewClientImpl intercepting url : " + requestUrl);
      System.out.println("WebViewClientImpl fileToUrlMap : " + new JSONObject(fileToUrlMap));
      if (!fileToUrlMap.containsKey(requestUrl))
        return super.shouldInterceptRequest(view, request);

      String fileExt = getFileExtFromUrl(requestUrl);
      String mimeType = getMimeTypeMap().get(fileExt);
      String encoding = getEncoding(fileExt);
      String filePath = fileToUrlMap.get(requestUrl);
      System.out.println("WebViewClientImpl filePath : " + filePath);
      WebResourceResponse webResponseFromFile = getWebResponseFromFile(fileExt, filePath, mimeType, encoding);
      if (webResponseFromFile != null) {
        System.out.println("WebViewClientImpl intercepting url success : " + filePath);
        // Whenever, font file .woff is loaded from cache No Access-Control-Allow-Origin
        // header added error occurs. This is  an error specific to woff files.
        // To solve this issue, add Access-Control-Allow-Origin header when returning the WebResourceResponse.
        Map<String, String> responseHeaders = webResponseFromFile.getResponseHeaders();
        Map<String, String> resHeaders = responseHeaders == null ? new HashMap<>() : responseHeaders;
        resHeaders.put("Access-Control-Allow-Origin", "*");
        webResponseFromFile.setResponseHeaders(resHeaders);
        return webResponseFromFile;
      }

      if ("text/html".equals(mimeType)) {
        return getHtmlMimeTypeWebResourceResponse(requestUrl, mimeType);
      }
      return super.shouldInterceptRequest(view, request);
    }

    /**
     * Creates a {@link WebViewClient} that passes arguments of callbacks methods to Dart.
     *
     * @param flutterApi handles sending messages to Dart
     * @param shouldOverrideUrlLoading whether loading a url should be overridden
     */
    public WebViewClientImpl(
        @NonNull WebViewClientFlutterApiImpl flutterApi, boolean shouldOverrideUrlLoading, Map<String, String> fileToUrlMap) {
      this.shouldOverrideUrlLoading = shouldOverrideUrlLoading;
      this.flutterApi = flutterApi;
      this.fileToUrlMap = fileToUrlMap;
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
      if (flutterApi != null) {
        flutterApi.onPageStarted(this, view, url, reply -> {});
      }
    }

    @Override
    public void onPageFinished(WebView view, String url) {
      if (flutterApi != null) {
        flutterApi.onPageFinished(this, view, url, reply -> {});
      }
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
      if (flutterApi != null) {
        flutterApi.onReceivedRequestError(this, view, request, error, reply -> {});
      }
    }

    @Override
    public void onReceivedError(
        WebView view, int errorCode, String description, String failingUrl) {
      if (flutterApi != null) {
        flutterApi.onReceivedError(
            this, view, (long) errorCode, description, failingUrl, reply -> {});
      }
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
      if (flutterApi != null) {
        flutterApi.requestLoading(this, view, request, reply -> {});
      }
      return shouldOverrideUrlLoading;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
      if (flutterApi != null) {
        flutterApi.urlLoading(this, view, url, reply -> {});
      }
      return shouldOverrideUrlLoading;
    }

    @Override
    public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
      // Deliberately empty. Occasionally the webview will mark events as having failed to be
      // handled even though they were handled. We don't want to propagate those as they're not
      // truly lost.
    }

    public void release() {
      if (flutterApi != null) {
        flutterApi.dispose(this, reply -> {});
      }
      flutterApi = null;
    }
  }

  /**
   * Implementation of {@link WebViewClientCompat} that passes arguments of callback methods to
   * Dart.
   */
  public static class WebViewClientCompatImpl extends WebViewClientCompat
      implements ReleasableWebViewClient {
    private @Nullable WebViewClientFlutterApiImpl flutterApi;
    private final boolean shouldOverrideUrlLoading;
    private final Map<String, String> fileToUrlMap;

    public WebViewClientCompatImpl(
        @NonNull WebViewClientFlutterApiImpl flutterApi, boolean shouldOverrideUrlLoading,
        Map<String, String> fileToUrlMap) {
      this.shouldOverrideUrlLoading = shouldOverrideUrlLoading;
      this.flutterApi = flutterApi;
      this.fileToUrlMap = fileToUrlMap;
    }

    @Nullable
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
      String requestUrl = null;
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
        requestUrl = request.getUrl().toString();
      }
      if (!fileToUrlMap.containsKey(requestUrl))
        return super.shouldInterceptRequest(view, request);

      Log.d("WebViewHostImpl", "intercepting url : " + requestUrl);
      String fileExt = getFileExtFromUrl(requestUrl);
      String mimeType = getMimeTypeMap().get(fileExt);
      String encoding = getEncoding(fileExt);
      String filePath = fileToUrlMap.get(requestUrl);
      WebResourceResponse webResponseFromFile = getWebResponseFromFile(fileExt, filePath, mimeType, encoding);
      if (webResponseFromFile != null) {
        Log.d("WebViewHostImpl", " : intercepting url success : " + filePath);
        // Whenever, font file .woff is loaded from cache No Access-Control-Allow-Origin
        // header added error occurs. This is  an error specific to woff files.
        // To solve this issue, add Access-Control-Allow-Origin header when returning the WebResourceResponse.
        Map<String, String> responseHeaders = webResponseFromFile.getResponseHeaders();
        Map<String, String> resHeaders = responseHeaders == null ? new HashMap<>() : responseHeaders;
        resHeaders.put("Access-Control-Allow-Origin", "*");
        webResponseFromFile.setResponseHeaders(resHeaders);
        return webResponseFromFile;
      }

      if ("text/html".equals(mimeType)) {
        return getHtmlMimeTypeWebResourceResponse(requestUrl, mimeType);
      }
      return super.shouldInterceptRequest(view, request);
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
      if (flutterApi != null) {
        flutterApi.onPageStarted(this, view, url, reply -> {});
      }
    }

    @Override
    public void onPageFinished(WebView view, String url) {
      if (flutterApi != null) {
        flutterApi.onPageFinished(this, view, url, reply -> {});
      }
    }

    // This method is only called when the WebViewFeature.RECEIVE_WEB_RESOURCE_ERROR feature is
    // enabled. The deprecated method is called when a device doesn't support this.
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @SuppressLint("RequiresFeature")
    @Override
    public void onReceivedError(
        @NonNull WebView view,
        @NonNull WebResourceRequest request,
        @NonNull WebResourceErrorCompat error) {
      if (flutterApi != null) {
        flutterApi.onReceivedRequestError(this, view, request, error, reply -> {});
      }
    }

    @Override
    public void onReceivedError(
        WebView view, int errorCode, String description, String failingUrl) {
      if (flutterApi != null) {
        flutterApi.onReceivedError(
            this, view, (long) errorCode, description, failingUrl, reply -> {});
      }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean shouldOverrideUrlLoading(
        @NonNull WebView view, @NonNull WebResourceRequest request) {
      if (flutterApi != null) {
        flutterApi.requestLoading(this, view, request, reply -> {});
      }
      return shouldOverrideUrlLoading;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
      if (flutterApi != null) {
        flutterApi.urlLoading(this, view, url, reply -> {});
      }
      return shouldOverrideUrlLoading;
    }

    @Override
    public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
      // Deliberately empty. Occasionally the webview will mark events as having failed to be
      // handled even though they were handled. We don't want to propagate those as they're not
      // truly lost.
    }

    public void release() {
      if (flutterApi != null) {
        flutterApi.dispose(this, reply -> {});
      }
      flutterApi = null;
    }
  }

  /** Handles creating {@link WebViewClient}s for a {@link WebViewClientHostApiImpl}. */
  public static class WebViewClientCreator {
    /**
     * Creates a {@link WebViewClient}.
     *
     * @param flutterApi handles sending messages to Dart
     * @return the created {@link WebViewClient}
     */
    public WebViewClient createWebViewClient(
        WebViewClientFlutterApiImpl flutterApi, boolean shouldOverrideUrlLoading,  Map<String, String> fileToUrlMap) {
      // WebViewClientCompat is used to get
      // shouldOverrideUrlLoading(WebView view, WebResourceRequest request)
      // invoked by the webview on older Android devices, without it pages that use iframes will
      // be broken when a navigationDelegate is set on Android version earlier than N.
      //
      // However, this if statement attempts to avoid using WebViewClientCompat on versions >= N due
      // to bug https://bugs.chromium.org/p/chromium/issues/detail?id=925887. Also, see
      // https://github.com/flutter/flutter/issues/29446.
      if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        return new WebViewClientImpl(flutterApi, shouldOverrideUrlLoading, fileToUrlMap);
      } else {
        return new WebViewClientCompatImpl(flutterApi, shouldOverrideUrlLoading, fileToUrlMap);
      }
    }
  }

  /**
   * Creates a host API that handles creating {@link WebViewClient}s.
   *
   * @param instanceManager maintains instances stored to communicate with Dart objects
   * @param webViewClientCreator handles creating {@link WebViewClient}s
   * @param flutterApi handles sending messages to Dart
   */
  public WebViewClientHostApiImpl(
      InstanceManager instanceManager,
      WebViewClientCreator webViewClientCreator,
      WebViewClientFlutterApiImpl flutterApi) {
    this.instanceManager = instanceManager;
    this.webViewClientCreator = webViewClientCreator;
    this.flutterApi = flutterApi;
  }

  @Override
  public void create(Long instanceId, Boolean shouldOverrideUrlLoading,  Map<String, String> fileToUrlMap) {
    final WebViewClient webViewClient =
        webViewClientCreator.createWebViewClient(flutterApi, shouldOverrideUrlLoading, fileToUrlMap);
    instanceManager.addDartCreatedInstance(webViewClient, instanceId);
  }

  private static WebResourceResponse getHtmlMimeTypeWebResourceResponse(String requestUrl, String mimeType) {
    HttpURLConnection urlConnection = null;
    try {
      URL url = new URL(requestUrl);
      urlConnection = (HttpURLConnection) url.openConnection();
      String contentDisposition = urlConnection.getHeaderField("content-disposition");
      if (contentDisposition == null || !contentDisposition.contains("attachment"))
        return null;
      InputStream in = new BufferedInputStream(urlConnection.getInputStream());
      return new WebResourceResponse(mimeType, "UTF-8", in);
    } catch (MalformedURLException e) {
      Log.d("WebViewHostImpl", "MalformedURLException : " + e.getMessage());
    } catch (IOException e) {
      Log.d("WebViewHostImpl", "IOException : " + e.getMessage());
    } finally {
      if (urlConnection != null) urlConnection.disconnect();
    }
    return null;
  }

  private static WebResourceResponse getWebResponseFromFile(String fileExt, String filePath, String mimeType, String encoding) {
    if (filePath == null || filePath.isEmpty()) return null;
    File file = new File(filePath);
    if (!file.exists()) return null;
    try {
      FileInputStream fileInputStream = new FileInputStream(file);
      if ("gz".equals(fileExt)) {
        GZIPInputStream gzipInputStream = new GZIPInputStream(fileInputStream);
        return new WebResourceResponse(mimeType, encoding, gzipInputStream);
      }
      return new WebResourceResponse(mimeType, encoding, fileInputStream);
    } catch (IOException e) {
      return null;
    }
  }

  public static Map<String, String> getMimeTypeMap() {
    Map<String, String> map = new HashMap<>();
    map.put("html", "text/html");
    map.put("gz", "text/html");
    map.put("css", "text/css");
    map.put("js", "text/javascript");
    map.put("png", "image/png");
    map.put("jpg", "image/jpeg");
    map.put("ico", "image/x-icon");
    map.put("svg", "image/svg+xml");
    map.put("webp", "image/webp");
    map.put("woff", "application/x-font-opentype");
    map.put("ttf", "application/x-font-opentype");
    map.put("eot", "application/x-font-opentype");
    return map;
  }

  public static String getEncoding(String fileExtension) {
    if ("gz".equals(fileExtension)) return "gzip";
    else return "UTF-8";
  }

  public static String getFileExtFromUrl(String url) {
    if (url == null || url.isEmpty()) return null;
    int lastIndexOfDot = url.lastIndexOf(".");
    int urlLength = url.length();
    return url.substring(lastIndexOfDot + 1, urlLength);
  }
}
