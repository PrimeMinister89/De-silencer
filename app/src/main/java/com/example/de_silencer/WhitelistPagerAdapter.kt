package com.example.de_silencer

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class WhitelistPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    // 我们一共有 2 个页面
    override fun getItemCount(): Int = 2

    // 第 0 页放普通电话，第 1 页放微信
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> PhoneFragment()
            1 -> WechatFragment()
            else -> PhoneFragment() // 兜底逻辑
        }
    }
}