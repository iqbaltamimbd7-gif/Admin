package com.saymabuilders.mobile;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;

/** Native, in-app PDF viewer used by the SAYMA WebView app. */
public class PdfViewerActivity extends Activity {
    public static final String EXTRA_FILE_PATH = "sayma_pdf_path";
    public static final String EXTRA_FILE_NAME = "sayma_pdf_name";

    private PdfRenderer renderer;
    private ParcelFileDescriptor fileDescriptor;
    private File pdfFile;
    private ImageView pageImage;
    private TextView pageLabel;
    private TextView titleLabel;
    private int currentPage = 0;
    private float zoom = 1.0f;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(30, 37, 53));
        getWindow().setNavigationBarColor(Color.BLACK);
        buildLayout();
        openPdf();
    }

    private void buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(239, 242, 247));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setPadding(dp(14), dp(12), dp(14), dp(10));
        toolbar.setBackgroundColor(Color.rgb(34, 43, 62));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("‹  Back");
        back.setOnClickListener(v -> finish());
        titleLabel = new TextView(this);
        titleLabel.setTextColor(Color.WHITE);
        titleLabel.setTextSize(14);
        titleLabel.setMaxLines(1);
        titleLabel.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.leftMargin = dp(10);
        top.addView(back);
        top.addView(titleLabel, titleParams);
        toolbar.addView(top);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(0, dp(10), 0, 0);
        Button previous = button("‹");
        previous.setOnClickListener(v -> showPage(currentPage - 1));
        Button zoomOut = button("−");
        zoomOut.setOnClickListener(v -> setZoom(Math.max(.7f, zoom - .15f)));
        pageLabel = new TextView(this);
        pageLabel.setTextColor(Color.WHITE);
        pageLabel.setTextSize(12);
        pageLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams pageParams = new LinearLayout.LayoutParams(0, -2, 1f);
        Button zoomIn = button("+");
        zoomIn.setOnClickListener(v -> setZoom(Math.min(2.5f, zoom + .15f)));
        Button next = button("›");
        next.setOnClickListener(v -> showPage(currentPage + 1));
        controls.addView(previous);
        controls.addView(zoomOut);
        controls.addView(pageLabel, pageParams);
        controls.addView(zoomIn);
        controls.addView(next);
        toolbar.addView(controls);
        root.addView(toolbar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(dp(8), dp(8), dp(8), dp(8));
        pageImage = new ImageView(this);
        pageImage.setAdjustViewBounds(true);
        pageImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        frame.addView(pageImage, imageParams);
        scroll.addView(frame);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView footer = new TextView(this);
        footer.setText("SAYMA BUILDERS · Secure document preview");
        footer.setTextColor(Color.rgb(104, 119, 139));
        footer.setTextSize(10);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(8), 0, dp(8));
        root.addView(footer);
        setContentView(root);
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(82, 95, 122)));
        b.setPadding(dp(8), 0, dp(8), 0);
        return b;
    }

    private void openPdf() {
        try {
            String path = getIntent().getStringExtra(EXTRA_FILE_PATH);
            String name = getIntent().getStringExtra(EXTRA_FILE_NAME);
            if (path == null || path.trim().isEmpty()) throw new IOException("PDF file path is missing.");
            titleLabel.setText(name == null ? "PDF document" : name);
            pdfFile = new File(path);
            fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(fileDescriptor);
            if (renderer.getPageCount() < 1) throw new IOException("This PDF has no pages.");
            showPage(0);
        } catch (Exception e) {
            pageLabel.setText(e.getMessage() == null ? "Could not open PDF" : e.getMessage());
        }
    }

    private void showPage(int index) {
        if (renderer == null) return;
        if (index < 0 || index >= renderer.getPageCount()) return;
        currentPage = index;
        PdfRenderer.Page page = null;
        try {
            page = renderer.openPage(index);
            int screenWidth = getResources().getDisplayMetrics().widthPixels - dp(32);
            float ratio = (float) page.getHeight() / Math.max(1, page.getWidth());
            int width = Math.max(500, Math.min(2200, (int) (screenWidth * 1.65f)));
            int height = Math.max(500, Math.min(3200, (int) (width * ratio)));
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            pageImage.setImageBitmap(bitmap);
            pageImage.setScaleX(zoom);
            pageImage.setScaleY(zoom);
            pageLabel.setText("Page " + (currentPage + 1) + " of " + renderer.getPageCount());
        } catch (Exception e) {
            pageLabel.setText("Could not render page " + (index + 1));
        } finally {
            if (page != null) page.close();
        }
    }

    private void setZoom(float value) {
        zoom = value;
        if (pageImage != null) {
            pageImage.setScaleX(zoom);
            pageImage.setScaleY(zoom);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        try { if (renderer != null) renderer.close(); } catch (Exception ignored) {}
        try { if (fileDescriptor != null) fileDescriptor.close(); } catch (Exception ignored) {}
        try { if (pdfFile != null && pdfFile.exists()) pdfFile.delete(); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
