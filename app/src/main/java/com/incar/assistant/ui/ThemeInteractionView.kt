package com.incar.assistant.ui

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.incar.assistant.config.AppConfigManager
import kotlin.math.min

enum class VoiceVisualState { IDLE, LISTENING, THINKING, SPEAKING }

/** Canvas implementation of the three prototype surfaces; scales across cockpit displays. */
class ThemeInteractionView(context: Context) : View(context) {
    var theme: String = AppConfigManager.THEME_ORB; set(value) { field = value; invalidate() }
    var state: VoiceVisualState = VoiceVisualState.IDLE; set(value) { field = value; invalidate() }
    var transcript: String = "点击光球开始说话"; set(value) { field = value; invalidate() }
    var onTap: (() -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f; private var orbX = 0f; private var orbY = 0f; private var downX = 0f; private var downY = 0f; private var moving = false
    init { setLayerType(LAYER_TYPE_SOFTWARE, null); postInvalidateOnAnimation() }
    override fun onDraw(c: Canvas) { phase += .045f; val w = width.toFloat(); val h = height.toFloat(); c.drawColor(Color.rgb(6,7,12)); when(theme) { AppConfigManager.THEME_CAPSULE -> capsule(c,w,h); AppConfigManager.THEME_AURORA -> aurora(c,w,h); else -> orb(c,w,h) }; postInvalidateOnAnimation() }
    private fun txt(c: Canvas,s:String,x:Float,y:Float,z:Float,color:Int,a:Paint.Align=Paint.Align.LEFT) { paint.shader=null; paint.style=Paint.Style.FILL; paint.color=color; paint.textSize=z; paint.textAlign=a; c.drawText(s,x,y,paint) }
    private fun orb(c:Canvas,w:Float,h:Float) { if(orbX==0f){orbX=w*.2f;orbY=h*.42f}; val p=1f+Math.sin(phase).toFloat()*.06f; paint.shader=RadialGradient(orbX,orbY,150f*p,intArrayOf(0x9938bdf8.toInt(),0x2238bdf8,Color.TRANSPARENT),null,Shader.TileMode.CLAMP);c.drawCircle(orbX,orbY,130f*p,paint);paint.shader=RadialGradient(orbX-12,orbY-12,52f,intArrayOf(Color.WHITE,0xff38bdf8.toInt(),0xff0e7490.toInt()),null,Shader.TileMode.CLAMP);c.drawCircle(orbX,orbY,48f,paint);paint.shader=null;paint.color=0xffffd166.toInt();c.drawOval(orbX-24,orbY-8,orbX-8,orbY+10,paint);c.drawOval(orbX+8,orbY-8,orbX+24,orbY+10,paint);for(i in 0 until(if(state==VoiceVisualState.IDLE)3 else 9)){val bh=8f+(Math.sin(phase*2+i).toFloat()+1)*15;paint.color=0xff67e8f9.toInt();c.drawRoundRect(orbX-28+i*7,orbY+70-bh,orbX-24+i*7,orbY+70,3f,3f,paint)};val b=RectF(orbX+72,orbY-70,min(w-20,orbX+350),orbY+20);paint.color=0xcc162536.toInt();c.drawRoundRect(b,18f,18f,paint);txt(c,transcript,b.left+18,b.centerY()+6,16f,Color.WHITE);txt(c,when(state){VoiceVisualState.IDLE->"ZEROAUTO 2.0";VoiceVisualState.LISTENING->"正在聆听";VoiceVisualState.THINKING->"正在思考";VoiceVisualState.SPEAKING->"正在播报"},24f,h-28f,13f,0xff94a3b8.toInt()) }
    private fun capsule(c:Canvas,w:Float,h:Float){val ww=min(580f,w-32f);val r=RectF((w-ww)/2,36f,(w+ww)/2,116f);paint.color=0xdd182535.toInt();c.drawRoundRect(r,40f,40f,paint);paint.style=Paint.Style.STROKE;paint.strokeWidth=1f;paint.color=0x6638bdf8;c.drawRoundRect(r,40f,40f,paint);paint.style=Paint.Style.FILL;paint.color=0xff38bdf8.toInt();c.drawCircle(r.left+38,r.centerY(),12f,paint);txt(c,if(state==VoiceVisualState.IDLE)"点击开始语音" else transcript,r.left+70,r.centerY()+6,18f,Color.WHITE);for(i in 0..3){val bh=14f+(Math.sin(phase*3+i).toFloat()+1)*18;paint.color=0xff2dd4bf.toInt();c.drawRoundRect(r.right-95+i*14,r.centerY()-bh/2,r.right-87+i*14,r.centerY()+bh/2,4f,4f,paint)}}
    private fun aurora(c:Canvas,w:Float,h:Float){val a=(38+(Math.sin(phase)+1)*24).toInt();paint.shader=LinearGradient(0f,0f,w,h,intArrayOf(Color.TRANSPARENT,a shl 24 or 0x7c3aed,a shl 24 or 0x0891b2,Color.TRANSPARENT),null,Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,paint);paint.shader=null;val b=RectF(28f,h-220f,w-28f,h-28f);paint.color=0xdd121b2b.toInt();c.drawRoundRect(b,28f,28f,paint);txt(c,"ZEROAUTO · 沉浸交互",b.left+24,b.top+38,13f,0xff67e8f9.toInt());txt(c,transcript,b.left+24,b.top+92,24f,Color.WHITE);txt(c,"${state.name}  ·  本地离线通道已就绪",b.left+24,b.bottom-28,13f,0xff94a3b8.toInt())}
    override fun onTouchEvent(e:MotionEvent):Boolean{when(e.actionMasked){MotionEvent.ACTION_DOWN->{downX=e.x;downY=e.y;moving=false;return true};MotionEvent.ACTION_MOVE->{if(Math.hypot((e.x-downX).toDouble(),(e.y-downY).toDouble())>18)moving=true;if(theme==AppConfigManager.THEME_ORB&&moving){orbX=e.x;orbY=e.y;invalidate()};return true};MotionEvent.ACTION_UP->{if(!moving)onTap?.invoke();return true}};return true}
}
