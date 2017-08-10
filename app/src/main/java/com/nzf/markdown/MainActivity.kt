package com.nzf.markdown

import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import com.nzf.markdown.app.MyApplication
import com.nzf.markdown.utils.FileUtils
import com.nzf.markdown.view.MaterialMenuDrawable
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    var isOpen: Boolean = false
    var materialMenu: MaterialMenuDrawable? = null
    var exitTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(tb_main_title)
        initView()

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    fun initView() {
        initMaterialMenu()
        var fileUtils = MyApplication().getFileUtils()
        fileUtils.showFileDir(fileUtils.ROOT_PATH!!.path)
    }

    private fun showAddDailog(mContext: Context) {
        var editText: EditText = EditText(mContext)
        var addDailog: AlertDialog = AlertDialog.Builder(this)
                .setTitle("添加文件夹")
                .setView(editText)
                .setPositiveButton("确认", DialogInterface.OnClickListener {
                    dialog, which ->
                    if (TextUtils.isEmpty(editText.text.toString().trim())) {
                        Toast.makeText(this, "文件夹名不能为空。", Toast.LENGTH_SHORT).show()
                        return@OnClickListener
                    }
                    var dirName: String = editText.text.toString().trim()

                    Log.i("Main : = ", MyApplication().getAppContext().toString())
                    var file: FileUtils = MyApplication().getFileUtils()
                    var dirSuccess: Boolean = file.newMDDir(dirName)
                    if (dirSuccess) {
                        Toast.makeText(this, "文件夹创建成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "文件夹创建失败", Toast.LENGTH_SHORT).show()
                    }
                })
                .setNegativeButton("取消", null)
                .show()

    }

    fun initMaterialMenu() {
        //初始化侧边栏按钮
        materialMenu = MaterialMenuDrawable(this, Color.WHITE, MaterialMenuDrawable.Stroke.THIN)

        tb_main_title.setNavigationIcon(materialMenu)
        tb_main_title.showOverflowMenu()
        tb_main_title.inflateMenu(R.menu.menu_main)
        tb_main_title.setNavigationOnClickListener {
            if (isOpen) {
                dl_main_body.closeDrawer(ll_main_setting)
                Log.i("main", "...........close!")
            } else {
                dl_main_body.openDrawer(ll_main_setting)
                Log.i("main", "...........open!")
            }

        }

        //添加按钮初始化
        tb_main_title.setOnMenuItemClickListener {
            item: MenuItem? ->
            when (item?.itemId) {
                R.id.item_main_add ->
                    Toast.makeText(this, "add", Toast.LENGTH_LONG).show()
                R.id.item_main_addtype ->
                    showAddDailog(this)
            }
            return@setOnMenuItemClickListener true
        }

        dl_main_body.setDrawerListener(object : DrawerLayout.SimpleDrawerListener() {

            override fun onDrawerSlide(drawerView: View?, slideOffset: Float) {
                var slideoff: Float
                if (isOpen) {
                    slideoff = 2 - slideOffset
                } else {
                    slideoff = slideOffset
                }
                materialMenu?.setTransformationOffset(
                        MaterialMenuDrawable.AnimationState.BURGER_ARROW,
                        slideoff
                )

            }

            override fun onDrawerClosed(drawerView: View?) {
                isOpen = false
            }

            override fun onDrawerOpened(drawerView: View?) {
                isOpen = true
            }

        })

    }

    //当用户连续按两次返回键的时候弹出是否退出对话框
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (System.currentTimeMillis() - exitTime > 2000) {
                val mHelperUtils: Any
                Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show()
                exitTime = System.currentTimeMillis()
            } else {
                finish()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

}