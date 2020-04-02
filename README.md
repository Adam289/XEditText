

# XEditText
Wrapped common usage of `EditText`. 

This is from **[XEditText](https://github.com/hgncxzy/XEditText)**, I change it to kotlin.



## Features
- To clear all text content just by one click on the right. The clear drawable is customizable.
- Perfectly fit for password input scenario. The toggle drawable is also customizable.
- You can customize the **Separator** or **Pattern** to separate the text content. But the text content by COPY, CUT, and PASTE will no longer be affected by **Separator** or **Pattern** you set.
- Be able to disable Emoji input.



## Usage
```xml
  <com.adam.widget.XEditText
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:hint="default, just likes EditText"
      app:x_disableClear="true"/>

  <com.adam.widget.XEditText
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:hint="clear drawable"/>

  <com.adam.widget.XEditText
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:hint="default password input"
      android:inputType="textPassword"/>

  <com.adam.widget.XEditText
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:hint="pwd input, custom drawables"
      android:inputType="textPassword" <!-- don't set gravity to center, 		   center_horizontal, right or end, otherwise the ClearDrawable will not appear. -->
      app:x_clearDrawable="@mipmap/ic_clear" <!--support vector drawable-->
      app:x_hidePwdDrawable="@mipmap/ic_hide" <!--support vector drawable-->
      app:x_showPwdDrawable="@mipmap/ic_show"/> <!--support vector drawable-->

  <com.adam.widget.XEditText
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:hint="the pattern to separate the content"
      app:x_pattern="3,4,4"
      app:x_separator=" "/>
```
**Need to listen to the focus state? Call `.setOnXTextChangeListener()` instead of `.setOnTextChangeListener()`**  
Check the sample for more details.

