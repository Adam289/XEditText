package com.adam.widget


import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.text.*
import android.text.InputFilter.LengthFilter
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.View.OnFocusChangeListener
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.widget.TextViewCompat


/**
 * @Description customizable edittext
 * To clear all text content just by one click on the right. The clear drawable is customizable.
 * Perfectly fit for password input scenario (android:inputType="textPassword"). The toggle drawable is also customizable.
 * You can customize the Separator or Pattern to separate the text content. But the text content by COPY, CUT, and PASTE will no longer be affected by Separator or Pattern you set.
 * Be able to disable emoji input.
 * @Author wucm
 * @Date 2020/4/2
 */
class XEditText @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = android.R.attr.editTextStyle) :
    AppCompatEditText(context, attrs, defStyleAttr) {

    private var mClearResId = 0
    private var mShowPwdResId = 0
    private var mHidePwdResId = 0
    private var mOldLength = 0
    private var mNowLength = 0
    private var mSelectionPos = 0
    private var mHasFocused = false
    private var mIsPwdInputType = false
    private var mIsPwdShow = false
    private var mBitmap: Bitmap? = null
    private var mLeft = 0
    private var mTop = 0
    private val mPadding: Int
    private val mTextWatcher: TextWatcher
    private var mTogglePwdDrawableEnable = false
    private var mClearDrawable: Drawable? = null
    private var mTogglePwdDrawable: Drawable? = null
    private var mXTextChangeListener: OnXTextChangeListener? = null
    private var mXFocusChangeListener: OnXFocusChangeListener? = null
    private var mOnClearListener: OnClearListener? = null
    private var mSeparator: String? = "" // Return the separator has been set, default = ""
    private var mDisableClear = false // disable clear, default = false
    private var mDisableEmoji = false // disable emoji and some special symbol input, default = false
    private var mPattern: IntArray? = null // pattern to separate. e.g.: mSeparator = "-", pattern = [3,4,4] -> xxx-xxxx-xxxx
    private var mIntervals: IntArray? = null// indexes of separators.
    private var mHasNoSeparator = true // true, the same as EditText. = false

    private fun initAttrs(context: Context, attrs: AttributeSet?, defStyleAttr: Int) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.XEditText, defStyleAttr, 0)
        mSeparator = a.getString(R.styleable.XEditText_x_separator)
        mDisableClear = a.getBoolean(R.styleable.XEditText_x_disableClear, false)
        mClearResId = a.getResourceId(R.styleable.XEditText_x_clearDrawable, -1)
        mTogglePwdDrawableEnable = a.getBoolean(R.styleable.XEditText_x_togglePwdDrawableEnable, true)
        mShowPwdResId = a.getResourceId(R.styleable.XEditText_x_showPwdDrawable, -1)
        mHidePwdResId = a.getResourceId(R.styleable.XEditText_x_hidePwdDrawable, -1)
        mDisableEmoji = a.getBoolean(R.styleable.XEditText_x_disableEmoji, false)
        val pattern = a.getString(R.styleable.XEditText_x_pattern)
        a.recycle()
        if (mSeparator == null) {
            mSeparator = ""
        }
        mHasNoSeparator = mSeparator.isNullOrEmpty()
        if (mSeparator!!.length > 0) {
            val inputType = inputType
            if (inputType == 2 || inputType == 8194 || inputType == 4098) { // if inputType is number, it can't insert mSeparator.
                setInputType(InputType.TYPE_CLASS_PHONE)
            }
        }
        if (!mDisableClear) {
            if (mClearResId == -1) mClearResId = R.drawable.x_et_svg_ic_clear_24dp
            mClearDrawable = AppCompatResources.getDrawable(context, mClearResId)
            if (mClearDrawable != null) {
                mClearDrawable!!.setBounds(
                    0, 0, mClearDrawable!!.intrinsicWidth,
                    mClearDrawable!!.intrinsicHeight
                )
                if (mClearResId == R.drawable.x_et_svg_ic_clear_24dp) DrawableCompat.setTint(mClearDrawable!!, currentHintTextColor)
            }
        }
        dealWithInputTypes(true)
        if (!mSeparator.isNullOrEmpty() && !mIsPwdInputType && pattern != null && !pattern.isNullOrEmpty()) {
            var ok = true
            if (pattern.contains(",")) {
                val split = pattern.split(",").toTypedArray()
                val array = IntArray(split.size)
                for (i in array.indices) {
                    try {
                        array[i] = split[i].toInt()
                    } catch (e: Exception) {
                        ok = false
                        break
                    }
                }
                if (ok) {
                    setPattern(array, mSeparator!!)
                }
            } else {
                try {
                    val i = pattern.toInt()
                    setPattern(intArrayOf(i), mSeparator!!)
                } catch (e: Exception) {
                    ok = false
                }
            }
            if (!ok) {
                Log.e("XEditText", "the Pattern format is incorrect!")
            }
        }
    }

    private fun dealWithInputTypes(fromXml: Boolean) {
        var inputType = inputType
        if (!fromXml) {
            inputType++
            if (inputType == 17) inputType++
        }
        mIsPwdInputType = mTogglePwdDrawableEnable && (inputType == 129 || inputType == 18 || inputType == 145 || inputType == 225)
        if (mIsPwdInputType) {
            mIsPwdShow = inputType == 145 // textVisiblePassword
            transformationMethod = if (mIsPwdShow) {
                HideReturnsTransformationMethod.getInstance()
            } else {
                PasswordTransformationMethod.getInstance()
            }
            if (mShowPwdResId == -1) mShowPwdResId = R.drawable.x_et_svg_ic_show_password_24dp
            if (mHidePwdResId == -1) mHidePwdResId = R.drawable.x_et_svg_ic_hide_password_24dp
            val tId = if (mIsPwdShow) mShowPwdResId else mHidePwdResId
            mTogglePwdDrawable = ContextCompat.getDrawable(context, tId)
            if (mTogglePwdDrawable != null) {
                if (mShowPwdResId == R.drawable.x_et_svg_ic_show_password_24dp || mHidePwdResId == R.drawable.x_et_svg_ic_hide_password_24dp) {
                    DrawableCompat.setTint(mTogglePwdDrawable!!, currentHintTextColor)
                }
                mTogglePwdDrawable!!.setBounds(0, 0, mTogglePwdDrawable!!.intrinsicWidth, mTogglePwdDrawable!!.intrinsicHeight)
            }
            if (mClearResId == -1) mClearResId = R.drawable.x_et_svg_ic_clear_24dp
            if (!mDisableClear) {
                mBitmap = getBitmapFromVectorDrawable(context, mClearResId, mClearResId == R.drawable.x_et_svg_ic_clear_24dp) // clearDrawable
            }
        }
        if (!fromXml) {
            textEx = textEx
            logicOfCompoundDrawables()
        }
    }

    private fun getBitmapFromVectorDrawable(context: Context, drawableId: Int, tint: Boolean): Bitmap? {
        var drawable = AppCompatResources.getDrawable(context, drawableId) ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            drawable = DrawableCompat.wrap(drawable).mutate()
        }
        if (tint) DrawableCompat.setTint(drawable, currentHintTextColor)
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    override fun setInputType(type: Int) {
        super.setInputType(type)
        dealWithInputTypes(false)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        logicOfCompoundDrawables()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mHasFocused && mBitmap != null && mIsPwdInputType && !isTextEmpty) {
            if (mLeft * mTop == 0) {
                mLeft = measuredWidth - paddingRight -
                        mTogglePwdDrawable!!.intrinsicWidth - mBitmap!!.width - mPadding
                mTop = measuredHeight - mBitmap!!.height shr 1
            }
            canvas.drawBitmap(mBitmap!!, mLeft.toFloat(), mTop.toFloat(), null)
        }
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) {
            return super.onTouchEvent(event)
        }
        if (event.action == MotionEvent.ACTION_UP) {
            performClick()
        }
        if (mHasFocused && mIsPwdInputType && event.action == MotionEvent.ACTION_UP) {
            val w = mTogglePwdDrawable!!.intrinsicWidth
            val h = mTogglePwdDrawable!!.intrinsicHeight
            val top = measuredHeight - h shr 1
            var right = measuredWidth - paddingRight
            var isAreaX = event.x <= right && event.x >= right - w
            val isAreaY = event.y >= top && event.y <= top + h
            if (isAreaX && isAreaY) {
                mIsPwdShow = !mIsPwdShow
                transformationMethod = if (mIsPwdShow) {
                    HideReturnsTransformationMethod.getInstance()
                } else {
                    PasswordTransformationMethod.getInstance()
                }
                setSelection(selectionStart, selectionEnd)
                mTogglePwdDrawable = ContextCompat.getDrawable(context, if (mIsPwdShow) mShowPwdResId else mHidePwdResId)
                if (mTogglePwdDrawable != null) {
                    if (mShowPwdResId == R.drawable.x_et_svg_ic_show_password_24dp ||
                        mHidePwdResId == R.drawable.x_et_svg_ic_hide_password_24dp
                    ) {
                        DrawableCompat.setTint(mTogglePwdDrawable!!, currentHintTextColor)
                    }
                    mTogglePwdDrawable!!.setBounds(
                        0, 0, mTogglePwdDrawable!!.intrinsicWidth,
                        mTogglePwdDrawable!!.intrinsicHeight
                    )
                    setCompoundDrawablesCompat(mTogglePwdDrawable)
                    invalidate()
                }
            }
            if (!mDisableClear) {
                right -= w + mPadding
                isAreaX = event.x <= right && event.x >= right - mBitmap!!.width
                if (isAreaX && isAreaY) {
                    error = null
                    setText("")
                    if (mOnClearListener != null) {
                        mOnClearListener!!.onClear()
                    }
                }
            }
        }
        if (mHasFocused && !mDisableClear && !mIsPwdInputType && event.action == MotionEvent.ACTION_UP) {
            val rect = mClearDrawable!!.bounds
            val rectW = rect.width()
            val rectH = rect.height()
            val top = measuredHeight - rectH shr 1
            val right = measuredWidth - paddingRight
            val isAreaX = event.x <= right && event.x >= right - rectW
            val isAreaY = event.y >= top && event.y <= top + rectH
            if (isAreaX && isAreaY) {
                error = null
                setText("")
                if (mOnClearListener != null) {
                    mOnClearListener!!.onClear()
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        val clipboardManager = context
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboardManager != null) {
            if (id == 16908320 || id == 16908321) { // catch CUT or COPY ops
                super.onTextContextMenuItem(id)
                val clip = clipboardManager.primaryClip
                if (clip != null) {
                    val item = clip.getItemAt(0)
                    if (item != null && item.text != null) {
                        val s = item.text.toString().replace(mSeparator!!, "")
                        clipboardManager.setPrimaryClip(ClipData.newPlainText(null, s))
                        return true
                    }
                }
            } else if (id == 16908322) { // catch PASTE ops
                val clip = clipboardManager.primaryClip
                if (clip != null) {
                    val item = clip.getItemAt(0)
                    if (item != null && item.text != null) {
                        val content = item.text.toString().replace(mSeparator!!, "")
                        val existedTxt = textNoneNull
                        var txt: String?
                        val start = selectionStart
                        val end = selectionEnd
                        if (start * end >= 0) {
                            val startHalfEx = existedTxt.substring(0, start).replace(mSeparator!!, "")
                            txt = startHalfEx + content
                            val endHalfEx = existedTxt.substring(end).replace(mSeparator!!, "")
                            txt += endHalfEx
                        } else {
                            txt = existedTxt.replace(mSeparator!!, "") + content
                        }
                        textEx = txt
                        return true
                    }
                }
            }
        }
        return super.onTextContextMenuItem(id)
    }

    // =========================== MyTextWatcher ================================
    private inner class MyTextWatcher : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            mOldLength = s.length
            if (mXTextChangeListener != null) {
                mXTextChangeListener!!.beforeTextChanged(s, start, count, after)
            }
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            mNowLength = s.length
            mSelectionPos = selectionStart
            if (mXTextChangeListener != null) {
                mXTextChangeListener!!.onTextChanged(s, start, before, count)
            }
        }

        override fun afterTextChanged(s: Editable) {
            logicOfCompoundDrawables()
            if (mSeparator!!.isEmpty()) {
                if (mXTextChangeListener != null) {
                    mXTextChangeListener!!.afterTextChanged(s)
                }
                return
            }
            removeTextChangedListener(mTextWatcher)
            val trimmedText: String
            trimmedText = if (mHasNoSeparator) {
                s.toString().trim { it <= ' ' }
            } else {
                s.toString().replace(mSeparator!!.toRegex(), "").trim { it <= ' ' }
            }
            setTextToSeparate(trimmedText, false)
            if (mXTextChangeListener != null) {
                s.clear()
                s.append(trimmedText)
                mXTextChangeListener!!.afterTextChanged(s)
            }
            addTextChangedListener(mTextWatcher)
        }
    }

    private fun logicOfCompoundDrawables() {
        if (!isEnabled || !mHasFocused || isTextEmpty && !mIsPwdInputType) {
            setCompoundDrawablesCompat(null)
            if (!isTextEmpty && mIsPwdInputType) {
                invalidate()
            }
        } else {
            if (mIsPwdInputType) {
                if (mShowPwdResId == R.drawable.x_et_svg_ic_show_password_24dp ||
                    mHidePwdResId == R.drawable.x_et_svg_ic_hide_password_24dp
                ) {
                    DrawableCompat.setTint(mTogglePwdDrawable!!, currentHintTextColor)
                }
                setCompoundDrawablesCompat(mTogglePwdDrawable)
            } else if (!isTextEmpty && !mDisableClear) {
                setCompoundDrawablesCompat(mClearDrawable)
            }
        }
    }

    private fun setCompoundDrawablesCompat(drawableRight: Drawable?) {
        val drawables = TextViewCompat.getCompoundDrawablesRelative(this)
        TextViewCompat.setCompoundDrawablesRelative(this, drawables[0], drawables[1], drawableRight, drawables[3])
    }

    private val isTextEmpty: Boolean
        private get() = textNoneNull.trim { it <= ' ' }.length == 0

    /**
     * set customize separator
     */
    fun setSeparator(separator: String) {
        this.mSeparator = separator
        mHasNoSeparator = TextUtils.isEmpty(this.mSeparator)
        if (separator.length > 0) {
            val inputType = inputType
            if (inputType == 2 || inputType == 8194 || inputType == 4098) { // if inputType is number, it can't insert mSeparator.
                setInputType(InputType.TYPE_CLASS_PHONE)
            }
        }
    }

    /**
     * set customize pattern
     *
     * @param pattern   e.g. pattern:{4,4,4,4}, separator:"-" to xxxx-xxxx-xxxx-xxxx
     * @param separator separator
     */
    fun setPattern(pattern: IntArray, separator: String) {
        setSeparator(separator)
        setPattern(pattern)
    }

    /**
     * set customize pattern
     *
     * @param pattern e.g. pattern:{4,4,4,4}, separator:"-" to xxxx-xxxx-xxxx-xxxx
     */
    fun setPattern(pattern: IntArray) {
        this.mPattern = pattern
        mIntervals = IntArray(pattern.size)
        var sum = 0
        for (i in pattern.indices) {
            sum += pattern[i]
            mIntervals!![i] = sum
        }
        /* When you set pattern, it will automatically compute the max length of characters and separators,
           so you don't need to set 'maxLength' attr in your xml any more(it won't work).*/
        val maxLength = mIntervals!![mIntervals!!.size - 1] + pattern.size - 1
        val filters = arrayOfNulls<InputFilter>(1)
        filters[0] = LengthFilter(maxLength)
        setFilters(filters)
    }

    /**
     * set CharSequence to separate
     */
    @Deprecated("Call {@link #setTextEx(CharSequence)} instead.")
    fun setTextToSeparate(c: CharSequence) {
        setTextToSeparate(c, true)
    }

    private fun setTextToSeparate(c: CharSequence, fromUser: Boolean) {
        if (c.length == 0 || mIntervals == null) {
            return
        }
        val builder = StringBuilder()
        var i = 0
        val length1 = c.length
        while (i < length1) {
            builder.append(c.subSequence(i, i + 1))
            var j = 0
            val length2 = mIntervals!!.size
            while (j < length2) {
                if (i == mIntervals!![j] && j < length2 - 1) {
                    builder.insert(builder.length - 1, mSeparator)
                    if (mSelectionPos == builder.length - 1 && mSelectionPos > mIntervals!![j]) {
                        if (mNowLength > mOldLength) { // inputted
                            mSelectionPos += mSeparator!!.length
                        } else { // deleted
                            mSelectionPos -= mSeparator!!.length
                        }
                    }
                }
                j++
            }
            i++
        }
        val text = builder.toString()
        setText(text)
        if (fromUser) {
            val maxLength = mIntervals!![mIntervals!!.size - 1] + mPattern!!.size - 1
            val index = Math.min(maxLength, text.length)
            try {
                setSelection(index)
            } catch (e: IndexOutOfBoundsException) {
                // Last resort (￣▽￣)
                val message = e.message
                if (!TextUtils.isEmpty(message) && message!!.contains(" ")) {
                    val last = message.lastIndexOf(" ")
                    val lenStr = message.substring(last + 1)
                    if (TextUtils.isDigitsOnly(lenStr)) {
                        setSelection(lenStr.toInt())
                    }
                }
            }
        } else {
            if (mSelectionPos > text.length) {
                mSelectionPos = text.length
            }
            if (mSelectionPos < 0) {
                mSelectionPos = 0
            }
            setSelection(mSelectionPos)
        }
    }

    /**
     * Get text string had been trimmed.
     */
    val textTrimmed: String
        get() = textEx.trim { it <= ' ' }

    /**
     * Get text string.
     * Call [.setText] or set text to separate by the pattern had been set.
     * <br></br>
     * It's especially convenient to call [.setText] in Kotlin.
     */
    var textEx: String
        get() = if (mHasNoSeparator) {
            textNoneNull
        } else {
            textNoneNull.replace(mSeparator!!.toRegex(), "")
        }
        set(text) {
            if (TextUtils.isEmpty(text) || mHasNoSeparator) {
                setText(text)
                setSelection(textNoneNull.length)
            } else {
                setTextToSeparate(text, true)
            }
        }

    /**
     * Get text String had been trimmed.
     *
     */
    @get:Deprecated("Call {@link #getTextTrimmed()} instead.")
    val trimmedString: String
        get() = if (mHasNoSeparator) {
            textNoneNull.trim { it <= ' ' }
        } else {
            textNoneNull.replace(mSeparator!!.toRegex(), "").trim { it <= ' ' }
        }

    private val textNoneNull: String
        private get() {
            val editable = text
            return editable?.toString() ?: ""
        }

    /**
     * @return has separator or not
     */
    fun hasNoSeparator(): Boolean {
        return mHasNoSeparator
    }

    /**
     * Set no separator, the same as EditText
     */
    fun setNoSeparator() {
        mHasNoSeparator = true
        mSeparator = ""
        mIntervals = null
    }

    /**
     * set true to disable Emoji and special symbol
     * @param mDisableEmoji true: disable emoji;
     * false: enable emoji
     */
    fun setDisableEmoji(mDisableEmoji: Boolean) {
        this.mDisableEmoji = mDisableEmoji
        filters = if (mDisableEmoji) {
            arrayOf<InputFilter>(EmojiExcludeFilter())
        } else {
            arrayOfNulls(0)
        }
    }

    /**
     * the same as EditText.addOnTextChangeListener(TextWatcher textWatcher)
     */
    fun setOnXTextChangeListener(listener: OnXTextChangeListener?) {
        mXTextChangeListener = listener
    }

    fun setOnXFocusChangeListener(listener: OnXFocusChangeListener?) {
        mXFocusChangeListener = listener
    }

    fun setOnClearListener(listener: OnClearListener?) {
        mOnClearListener = listener
    }

    interface OnXTextChangeListener {
        fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int)
        fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int)
        fun afterTextChanged(s: Editable?)
    }

    interface OnXFocusChangeListener {
        fun onFocusChange(v: View?, hasFocus: Boolean)
    }

    interface OnClearListener {
        fun onClear()
    }

    override fun onSaveInstanceState(): Parcelable? {
        val bundle = Bundle()
        bundle.putParcelable("save_instance", super.onSaveInstanceState())
        bundle.putString("separator", mSeparator)
        bundle.putIntArray("pattern", mPattern)
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state is Bundle) {
            val bundle = state
            mSeparator = bundle.getString("separator")
            mPattern = bundle.getIntArray("pattern")
            mHasNoSeparator = TextUtils.isEmpty(mSeparator)
            if (mPattern != null) {
                setPattern(mPattern!!)
            }
            super.onRestoreInstanceState(bundle.getParcelable("save_instance"))
            return
        }
        super.onRestoreInstanceState(state)
    }

    /**
     * disable emoji and special symbol input
     */
    private class EmojiExcludeFilter : InputFilter {
        override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence {
            for (i in start until end) {
                val type = Character.getType(source[i])
                if (type == Character.SURROGATE.toInt() || type == Character.OTHER_SYMBOL.toInt()) {
                    return ""
                }
            }
            return ""
        }
    }

    init {
        initAttrs(context, attrs, defStyleAttr)
        if (mDisableEmoji) {
            filters = arrayOf<InputFilter>(EmojiExcludeFilter())
        }
        mTextWatcher = MyTextWatcher()
        addTextChangedListener(mTextWatcher)
        onFocusChangeListener = OnFocusChangeListener { v, hasFocus ->
            mHasFocused = hasFocus
            logicOfCompoundDrawables()
            if (mXFocusChangeListener != null) {
                mXFocusChangeListener!!.onFocusChange(v, hasFocus)
            }
        }
        mPadding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 4f,
            Resources.getSystem().displayMetrics
        ).toInt()
    }
}