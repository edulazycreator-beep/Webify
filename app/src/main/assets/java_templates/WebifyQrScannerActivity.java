package {{PACKAGE}};

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Pemindai QR & barcode native (ZXing core + android.hardware.Camera) dengan viewfinder,
 * garis laser animasi, dan tombol senter. Hasil dikembalikan ke MainActivity → callback JS.
 */
@SuppressWarnings("deprecation")
public class WebifyQrScannerActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private static final int REQ_CAMERA = 501;
    private Camera camera;
    private SurfaceView surface;
    private ViewfinderView viewfinder;
    private HandlerThread decodeThread;
    private Handler decodeHandler;
    private volatile boolean decoding = false;
    private volatile boolean finished = false;
    private boolean torchOn = false;
    private final MultiFormatReader reader = new MultiFormatReader();
    private int previewW, previewH;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Map<DecodeHintType, Object> hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(
                BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX, BarcodeFormat.AZTEC, BarcodeFormat.PDF_417,
                BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
                BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93, BarcodeFormat.ITF, BarcodeFormat.CODABAR));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        reader.setHints(hints);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        surface = new SurfaceView(this);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));
        viewfinder = new ViewfinderView(this);
        root.addView(viewfinder, new FrameLayout.LayoutParams(-1, -1));

        TextView hint = new TextView(this);
        hint.setText("Arahkan kamera ke QR code atau barcode");
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        hint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        hlp.topMargin = dp(96);
        root.addView(hint, hlp);

        TextView close = pill("✕  Tutup");
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finishWith(null); }
        });
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, dp(44), Gravity.BOTTOM | Gravity.START);
        clp.setMargins(dp(24), 0, 0, dp(48));
        root.addView(close, clp);

        final TextView torch = pill("Senter");
        torch.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleTorch(); torch.setText(torchOn ? "Senter: ON" : "Senter"); }
        });
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-2, dp(44), Gravity.BOTTOM | Gravity.END);
        tlp.setMargins(0, 0, dp(24), dp(48));
        root.addView(torch, tlp);

        setContentView(root);
        surface.getHolder().addCallback(this);
        decodeThread = new HandlerThread("webify-qr");
        decodeThread.start();
        decodeHandler = new Handler(decodeThread.getLooper());

        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    private TextView pill(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(20), 0, dp(20), 0);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0x99000000);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), 0x55FFFFFF);
        t.setBackground(bg);
        return t;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (surface.getHolder().getSurface().isValid()) openCamera(surface.getHolder());
            } else {
                Toast.makeText(this, "Izin kamera diperlukan untuk memindai", Toast.LENGTH_LONG).show();
                finishWith(null);
            }
        }
    }

    // ------------------------------------------------------------------ Kamera
    @Override public void surfaceCreated(SurfaceHolder holder) {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera(holder);
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        releaseCamera();
    }

    private void openCamera(SurfaceHolder holder) {
        if (camera != null) return;
        try {
            int backId = 0;
            Camera.CameraInfo info = new Camera.CameraInfo();
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) { backId = i; break; }
            }
            camera = Camera.open(backId);
            Camera.getCameraInfo(backId, info);
            camera.setDisplayOrientation(displayOrientation(info));
            Camera.Parameters p = camera.getParameters();
            Camera.Size best = choosePreviewSize(p.getSupportedPreviewSizes());
            p.setPreviewSize(best.width, best.height);
            List<String> focus = p.getSupportedFocusModes();
            if (focus != null && focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (focus != null && focus.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                p.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }
            camera.setParameters(p);
            previewW = best.width;
            previewH = best.height;
            camera.setPreviewDisplay(holder);
            camera.setPreviewCallback(this);
            camera.startPreview();
        } catch (Exception e) {
            Toast.makeText(this, "Kamera tidak dapat dibuka: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finishWith(null);
        }
    }

    private int displayOrientation(Camera.CameraInfo info) {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
            default: degrees = 0;
        }
        return (info.orientation - degrees + 360) % 360;
    }

    private Camera.Size choosePreviewSize(List<Camera.Size> sizes) {
        Camera.Size best = sizes.get(0);
        long target = 1280L * 720L;
        long bestDiff = Long.MAX_VALUE;
        for (Camera.Size s : sizes) {
            long diff = Math.abs((long) s.width * s.height - target);
            if (diff < bestDiff) { best = s; bestDiff = diff; }
        }
        return best;
    }

    private void toggleTorch() {
        if (camera == null) return;
        try {
            Camera.Parameters p = camera.getParameters();
            List<String> modes = p.getSupportedFlashModes();
            if (modes == null || !modes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                Toast.makeText(this, "Senter tidak tersedia", Toast.LENGTH_SHORT).show();
                return;
            }
            torchOn = !torchOn;
            p.setFlashMode(torchOn ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
            camera.setParameters(p);
        } catch (Exception ignored) { }
    }

    private void releaseCamera() {
        if (camera != null) {
            try {
                camera.setPreviewCallback(null);
                camera.stopPreview();
                camera.release();
            } catch (Exception ignored) { }
            camera = null;
        }
    }

    // ------------------------------------------------------------------ Decode
    @Override
    public void onPreviewFrame(final byte[] data, Camera cam) {
        if (decoding || finished || data == null) return;
        decoding = true;
        final int w = previewW;
        final int h = previewH;
        decodeHandler.post(new Runnable() {
            @Override public void run() {
                String text = decode(data, w, h);
                if (text == null) {
                    // barcode 1D yang dipegang vertikal: coba frame yang diputar 90°
                    byte[] rotated = rotateLuminance(data, w, h);
                    text = decode(rotated, h, w);
                }
                decoding = false;
                if (text != null && !finished) {
                    final String result = text;
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override public void run() { vibrate(); finishWith(result); }
                    });
                }
            }
        });
    }

    private String decode(byte[] data, int w, int h) {
        try {
            int size = Math.min(w, h) * 3 / 4;
            int left = (w - size) / 2;
            int top = (h - size) / 2;
            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(data, w, h, left, top, size, size, false);
            Result r = reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(source)));
            return r.getText();
        } catch (Exception e) {
            return null;
        } finally {
            reader.reset();
        }
    }

    private static byte[] rotateLuminance(byte[] data, int w, int h) {
        byte[] out = new byte[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[x * h + (h - y - 1)] = data[y * w + x];
            }
        }
        return out;
    }

    private void vibrate() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(60);
        } catch (Exception ignored) { }
    }

    private void finishWith(String result) {
        if (finished) return;
        finished = true;
        Intent data = new Intent();
        data.putExtra("callback", getIntent().getStringExtra("callback"));
        if (result != null) data.putExtra("result", result);
        setResult(result != null ? RESULT_OK : RESULT_CANCELED, data);
        finish();
    }

    @Override
    public void onBackPressed() {
        finishWith(null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (surface.getHolder().getSurface().isValid()
                && checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera(surface.getHolder());
        }
    }

    @Override
    protected void onDestroy() {
        releaseCamera();
        if (decodeThread != null) decodeThread.quitSafely();
        super.onDestroy();
    }

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Overlay bingkai bidik: area gelap di luar kotak, sudut tebal, garis laser bergerak. */
    static class ViewfinderView extends View {
        private final Paint dim = new Paint();
        private final Paint corner = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint laser = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float laserPos = 0f;
        private float laserDir = 1f;

        ViewfinderView(Context c) {
            super(c);
            dim.setColor(0x99000000);
            corner.setColor(BuildConfig.PRIMARY_COLOR | 0xFF000000);
            corner.setStyle(Paint.Style.STROKE);
            corner.setStrokeWidth(getResources().getDisplayMetrics().density * 4);
            corner.setStrokeCap(Paint.Cap.ROUND);
            laser.setColor(0xCCFF3B30);
            laser.setStrokeWidth(getResources().getDisplayMetrics().density * 2);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            int size = (int) (Math.min(w, h) * 0.7f);
            Rect frame = new Rect((w - size) / 2, (h - size) / 2, (w + size) / 2, (h + size) / 2);
            canvas.drawRect(0, 0, w, frame.top, dim);
            canvas.drawRect(0, frame.bottom, w, h, dim);
            canvas.drawRect(0, frame.top, frame.left, frame.bottom, dim);
            canvas.drawRect(frame.right, frame.top, w, frame.bottom, dim);
            float len = size * 0.12f;
            RectF f = new RectF(frame);
            canvas.drawLine(f.left, f.top, f.left + len, f.top, corner);
            canvas.drawLine(f.left, f.top, f.left, f.top + len, corner);
            canvas.drawLine(f.right, f.top, f.right - len, f.top, corner);
            canvas.drawLine(f.right, f.top, f.right, f.top + len, corner);
            canvas.drawLine(f.left, f.bottom, f.left + len, f.bottom, corner);
            canvas.drawLine(f.left, f.bottom, f.left, f.bottom - len, corner);
            canvas.drawLine(f.right, f.bottom, f.right - len, f.bottom, corner);
            canvas.drawLine(f.right, f.bottom, f.right, f.bottom - len, corner);
            float y = f.top + f.height() * laserPos;
            canvas.drawLine(f.left + len / 2, y, f.right - len / 2, y, laser);
            laserPos += 0.012f * laserDir;
            if (laserPos > 1f) { laserPos = 1f; laserDir = -1f; }
            if (laserPos < 0f) { laserPos = 0f; laserDir = 1f; }
            postInvalidateOnAnimation();
        }
    }
}
