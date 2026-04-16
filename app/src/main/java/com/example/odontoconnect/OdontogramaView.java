package com.example.odontoconnect;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.Map;

public class OdontogramaView extends View {

    public static final int SANO      = 0; // Blanco
    public static final int CARIES    = 1; // Rojo
    public static final int OBTURADO  = 2; // Azul
    public static final int AUSENTE   = 3; // Gris
    public static final int CORONA    = 4; // Amarillo
    public static final int FRACTURA  = 5; // Naranja
    public static final int TRATANDO  = 6; // Verde

    private static final int[] COLORES = {
            Color.WHITE,
            Color.rgb(220, 50,  50),
            Color.rgb(50,  100, 220),
            Color.rgb(80,  80,  80),
            Color.rgb(230, 180, 0),
            Color.rgb(230, 120, 0),
            Color.rgb(50,  160, 80),
    };

    private static final int[] SUPERIORES = {
            18,17,16,15,14,13,12,11, 21,22,23,24,25,26,27,28
    };
    private static final int[] INFERIORES = {
            48,47,46,45,44,43,42,41, 31,32,33,34,35,36,37,38
    };

    private final Map<Integer, Integer> estado = new HashMap<>();
    private final Map<Integer, String>  notas  = new HashMap<>();

    private Paint pFill, pStroke, pText, pBg;
    private float dw, dh, sx, sy, iy;

    private OnDienteClickListener listener;

    public interface OnDienteClickListener {
        void onDienteClick(int numero, int condicionActual);
    }

    public OdontogramaView(Context ctx) { super(ctx); init(); }
    public OdontogramaView(Context ctx, AttributeSet a) { super(ctx,a); init(); }

    private void init() {
        pFill   = new Paint(Paint.ANTI_ALIAS_FLAG);
        pFill.setStyle(Paint.Style.FILL);

        pStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setColor(Color.rgb(100,150,200));
        pStroke.setStrokeWidth(2f);

        pText   = new Paint(Paint.ANTI_ALIAS_FLAG);
        pText.setColor(Color.rgb(30,50,100));
        pText.setTextAlign(Paint.Align.CENTER);
        pText.setTypeface(Typeface.DEFAULT_BOLD);

        pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        pBg.setColor(Color.rgb(240,246,255));
        pBg.setStyle(Paint.Style.FILL);

        for (int d : SUPERIORES) estado.put(d, SANO);
        for (int d : INFERIORES) estado.put(d, SANO);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float pad = w * 0.01f;
        dw = (w - pad*2) / 16f;
        dh = h * 0.33f;
        sx = pad;
        sy = h * 0.06f;
        iy = h * 0.58f;
        pText.setTextSize(dw * 0.32f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0,0,getWidth(),getHeight(), pBg);

        // Línea encía
        Paint lp = new Paint();
        lp.setColor(Color.rgb(180,200,230));
        lp.setStrokeWidth(2f);
        canvas.drawLine(sx, getHeight()*0.5f,
                getWidth()-sx, getHeight()*0.5f, lp);

        // Etiquetas
        Paint lbl = new Paint(Paint.ANTI_ALIAS_FLAG);
        lbl.setColor(Color.rgb(120,150,190));
        lbl.setTextSize(dw * 0.28f);
        lbl.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("SUPERIOR", getWidth()/2f, sy - 6, lbl);
        canvas.drawText("INFERIOR", getWidth()/2f, iy - 6, lbl);

        for (int i=0; i<SUPERIORES.length; i++)
            dibujar(canvas, SUPERIORES[i], sx+i*dw, sy, true);
        for (int i=0; i<INFERIORES.length; i++)
            dibujar(canvas, INFERIORES[i], sx+i*dw, iy, false);
    }

    private void dibujar(Canvas c, int num, float x, float y, boolean sup) {
        int cond  = estado.getOrDefault(num, SANO);
        float r   = dw * 0.28f;
        RectF rect = new RectF(x+1, y, x+dw-1, y+dh);

        pFill.setColor(COLORES[cond]);
        c.drawRoundRect(rect, r, r, pFill);

        pStroke.setStrokeWidth(cond==SANO ? 1.5f : 3f);
        c.drawRoundRect(rect, r, r, pStroke);

        float ty = sup ? y+dh+pText.getTextSize()*1.1f : y-pText.getTextSize()*0.2f;
        c.drawText(String.valueOf(num), x+dw/2f, ty, pText);

        if (notas.containsKey(num)) {
            Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
            dot.setColor(Color.rgb(50,150,200));
            c.drawCircle(x+dw-5, y+5, 4, dot);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getAction() != MotionEvent.ACTION_UP) return true;
        float tx=ev.getX(), ty=ev.getY();
        int found=-1;
        for (int i=0; i<SUPERIORES.length; i++) {
            float x=sx+i*dw;
            if (tx>=x && tx<=x+dw && ty>=sy && ty<=sy+dh) {
                found=SUPERIORES[i]; break;
            }
        }
        if (found==-1) for (int i=0; i<INFERIORES.length; i++) {
            float x=sx+i*dw;
            if (tx>=x && tx<=x+dw && ty>=iy && ty<=iy+dh) {
                found=INFERIORES[i]; break;
            }
        }
        if (found!=-1 && listener!=null)
            listener.onDienteClick(found, estado.getOrDefault(found, SANO));
        return true;
    }

    public void setCondicion(int num, int cond) { estado.put(num, cond); invalidate(); }
    public void setNota(int num, String nota) {
        if (nota!=null && !nota.isEmpty()) notas.put(num, nota);
        else notas.remove(num);
        invalidate();
    }
    public Map<Integer,Integer> getEstado() { return new HashMap<>(estado); }
    public Map<Integer,String>  getNotas()  { return new HashMap<>(notas);  }

    public void cargarEstado(Map<String,Object> data) {
        if (data==null) return;
        for (Map.Entry<String,Object> e : data.entrySet()) {
            try { estado.put(Integer.parseInt(e.getKey()),
                    ((Number)e.getValue()).intValue()); }
            catch (Exception ignored) {}
        }
        invalidate();
    }

    public void setOnDienteClickListener(OnDienteClickListener l) { listener=l; }

    @Override
    protected void onMeasure(int ws, int hs) {
        int w = MeasureSpec.getSize(ws);
        setMeasuredDimension(w, (int)(w*0.58f));
    }
}
