package agu;

import java.util.EmptyStackException;
import java.util.Stack;

import android.annotation.SuppressLint;
import android.graphics.BitmapFactory.Options;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;

public abstract class ResourcePool<T> {
	private Stack<T> stack;
	
	public static final ResourcePool<Paint> PAINT = new ResourcePool<Paint>() {
		@Override
		protected Paint newInstance() {
			return new Paint();
		}

		@Override
		protected void reset(Paint obj) {
			obj.reset();
		}
	};
	
	public static final ResourcePool<Rect> RECT = new ResourcePool<Rect>() {
		@Override
		protected Rect newInstance() {
			return new Rect();
		}
		
		@Override
		protected void reset(Rect obj) {
			obj.set(0, 0, 0, 0);
		}
	};
	
	public static final ResourcePool<RectF> RECTF = new ResourcePool<RectF>() {
		@Override
		protected RectF newInstance() {
			return new RectF();
		}

		@Override
		protected void reset(RectF obj) {
			obj.set(0, 0, 0, 0);
		}
	};
	
	public static final ResourcePool<Point> POINT = new ResourcePool<Point>() {
		@Override
		protected Point newInstance() {
			return new Point();
		}
		
		@Override
		protected void reset(Point obj) {
			obj.set(0, 0);
		}
	};
	
	public static final ResourcePool<Matrix> MATRIX = new ResourcePool<Matrix>() {
		@Override
		protected Matrix newInstance() {
			return new Matrix();
		}
		
		@Override
		protected void reset(Matrix obj) {
			obj.reset();
		}
	};
	
	public static final ResourcePool<Options> OPTIONS = new ResourcePool<Options>() {
		@Override
		protected Options newInstance() {
			return new Options();
		}
		
		@SuppressLint("NewApi")
		@Override
		protected void reset(Options obj) {
			if (Build.VERSION.SDK_INT >= 10) {
				obj.inPreferQualityOverSpeed = false;
				if (Build.VERSION.SDK_INT >= 11) {
					obj.inBitmap = null;
					obj.inMutable = false;
					
					if (Build.VERSION.SDK_INT >= 19) {
						obj.inPremultiplied = true;
					}
				}
			}
			
			obj.inDensity = 0;
			obj.inDither = true;
			obj.inInputShareable = false;
			obj.inJustDecodeBounds = false;
			obj.inPreferredConfig = null;
			obj.inPurgeable = false;
			obj.inSampleSize = 0;
			obj.inScaled = true;
			obj.inScreenDensity = 0;
			obj.inTargetDensity = 0;
			obj.inTempStorage = null;
			obj.mCancel = false;
			obj.outHeight = 0;
			obj.outMimeType = null;
			obj.outWidth = 0;
		}
	};
	
	public ResourcePool() {
		this.stack = new Stack<T>();
	}
	
	protected abstract T newInstance();
	
	protected void reset(T obj) {
	}

	public T obtain() {
		return obtain(true);
	}
	
	public T obtain(boolean reset) {
		try {
			final T obj = stack.pop();
			if (reset) reset(obj);
			return obj;
		} catch (EmptyStackException e) {
			return newInstance();
		}
	}
	
	public void recycle(T obj) {
		if (obj == null) return;
		stack.push(obj);
	}
}