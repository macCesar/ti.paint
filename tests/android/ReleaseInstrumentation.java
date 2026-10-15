package ti.paint.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.concurrent.Semaphore;

/** Runs against a real Titanium application and the packaged module, without mocks. */
public class ReleaseInstrumentation extends Instrumentation {
    private Activity activity;
    private Class<?> proxyClass;

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle results = new Bundle();
        try {
            ActivityMonitor monitor = addMonitor("org.appcelerator.titanium.TiActivity", null, false);
            getTargetContext().startActivity(getTargetContext().getPackageManager()
                .getLaunchIntentForPackage(getTargetContext().getPackageName()));
            activity = waitForMonitorWithTimeout(monitor, 30000);
            removeMonitor(monitor);
            check(activity != null, "Titanium window did not open");
            long deadline = SystemClock.uptimeMillis() + 30000;
            boolean[] ready = {false};
            while (!ready[0] && SystemClock.uptimeMillis() < deadline) {
                runOnMainSync(() -> ready[0] = hasReadyLabel(activity.getWindow().getDecorView()));
                SystemClock.sleep(100);
            }
            check(ready[0], "Titanium fixture did not become ready");
            proxyClass = getTargetContext().getClassLoader()
                .loadClass("ti.modules.titanium.paint.PaintViewProxy");
            for (int mode = 0; mode < 3; mode++) {
                final int scenario = mode;
                Throwable[] failure = {null};
                runOnMainSync(() -> {
                    try { verifyRelease(scenario); } catch (Throwable error) { failure[0] = error; }
                });
                if (failure[0] != null) throw new AssertionError("scenario " + mode, failure[0]);
            }
            results.putString("stream", "PASS: idle, playing and paused release; callbacks, data, late calls and recreation\n");
            finish(Activity.RESULT_OK, results);
        } catch (Throwable error) {
            results.putString("stream", android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, results);
        }
    }

    private boolean hasReadyLabel(View view) {
        if (view instanceof TextView && "paint-test-ready".contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (hasReadyLabel(group.getChildAt(i))) return true;
        }
        return false;
    }

    private void verifyRelease(int mode) throws Exception {
        Object proxy = proxyClass.getConstructor().newInstance();
        call(proxy, "setActivity", new Class<?>[]{Activity.class}, activity);
        Object ui = call(proxy, "getOrCreateView");
        View nativeView = (View) get(ui, "tiPaintView");
        nativeView.layout(0, 0, 1000, 1000);
        call(proxy, "moveTo", new Class<?>[]{int.class, int.class}, 10, 10);
        call(proxy, "lineTo", new Class<?>[]{int.class, int.class}, 100, 100);
        call(proxy, "moveTo", new Class<?>[]{int.class, int.class}, 200, 200);
        call(proxy, "moveTo", new Class<?>[]{int.class, int.class}, 300, 300);
        call(proxy, "undo");
        check(!((Collection<?>) get(nativeView, "undoPaths")).isEmpty(), "fixture must retain undo data");
        check(get(nativeView, "tiBitmap") != null, "fixture must allocate drawing bitmap");
        if (mode > 0) {
            call(proxy, "playbackDrawing", new Class<?>[]{float.class}, 60f);
            ((Runnable) get(nativeView, "playbackRunnable")).run();
            check(!((Collection<?>) get(nativeView, "playbackPaths")).isEmpty(), "fixture must retain playback data");
            if (mode == 2) call(proxy, "pausePlayback");
        }
        Handler playbackHandler = (Handler) get(nativeView, "playbackHandler");
        Runnable pending = (Runnable) get(nativeView, "playbackRunnable");
        if (mode > 0) check(playbackHandler.hasCallbacks(pending), "fixture must queue playback");

        call(proxy, "releaseViews");
        check(get(proxy, "paintView") == null, "released proxy retains UIPaintView");
        check(get(ui, "tiPaintView") == null, "released wrapper retains native view");
        for (String field : new String[]{"tiBitmap", "tiCanvas", "tiImage", "mPath", "pp", "playbackRunnable"}) {
            check(get(nativeView, field) == null, "released native view retains " + field);
        }
        for (String field : new String[]{"tiPaths", "undoPaths", "playbackPaths"}) {
            check(((Collection<?>) get(nativeView, field)).isEmpty(), "released view retains " + field);
        }
        if (pending != null) {
            check(!playbackHandler.hasCallbacks(pending), "release leaves a scheduled playback callback");
            pending.run();
            check(!playbackHandler.hasCallbacks(pending), "late callback reschedules playback");
        }
        nativeView.layout(0, 0, 1100, 1100);
        Bitmap target = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        nativeView.draw(new Canvas(target));
        target.recycle();
        check(get(nativeView, "tiBitmap") == null, "late layout recreates released bitmap");
        // Calls from JavaScript that still holds the proxy must be harmless.
        call(proxy, "setStrokeWidth", new Class<?>[]{Object.class}, 5);
        call(proxy, "setStrokeColor", new Class<?>[]{String.class}, "red");
        call(proxy, "setEraseMode", new Class<?>[]{Boolean.class}, true);
        call(proxy, "setStrokeAlpha", new Class<?>[]{int.class}, 128);
        call(proxy, "setImage", new Class<?>[]{String.class}, "unused.png");
        call(proxy, "fill", new Class<?>[]{String.class}, "blue");
        call(proxy, "moveTo", new Class<?>[]{int.class, int.class}, 1, 1);
        call(proxy, "lineTo", new Class<?>[]{int.class, int.class}, 2, 2);
        for (String method : new String[]{"undo", "redo", "clear", "pausePlayback", "resumePlayback", "stopPlayback"}) call(proxy, method);
        call(proxy, "enable", new Class<?>[]{boolean.class}, true);
        call(proxy, "playbackDrawing", new Class<?>[]{float.class}, 10f);
        call(proxy, "setPlaybackSpeed", new Class<?>[]{float.class}, 2f);
        call(proxy, "loadStrokes", new Class<?>[]{Object[].class}, (Object) new Object[0]);
        check(((Object[]) call(proxy, "getStrokesData")).length == 0, "released proxy returns strokes");
        check(((Float) call(proxy, "getPlaybackProgress")) == 0f, "released proxy reports progress");
        call(proxy, "releaseViews");
        call(ui, "release");

        // Simulate blocking messages that were queued before release and dispatched afterward.
        Handler handler = (Handler) get(proxy, "handler");
        Class<?> resultClass = getTargetContext().getClassLoader()
            .loadClass("org.appcelerator.kroll.common.AsyncResult");
        Semaphore[] messages = new Semaphore[3];
        for (int i = 0; i < messages.length; i++) {
            messages[i] = (Semaphore) resultClass.getConstructor(Object.class).newInstance((Object) new Object[0]);
            handler.dispatchMessage(handler.obtainMessage(60000 + i, messages[i]));
            check(messages[i].tryAcquire(), "queued caller left blocked after release");
        }
        Object replacement = call(proxy, "getOrCreateView");
        check(replacement != ui && get(replacement, "tiPaintView") != nativeView, "proxy must create a fresh view");
        call(proxy, "releaseViews");
    }

    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static Object call(Object object, String name) throws Exception {
        return call(object, name, new Class<?>[0]);
    }

    private static Object call(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = object.getClass().getMethod(name, types);
        return method.invoke(object, args);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
