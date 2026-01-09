package com.campilot.di

import android.content.Context
import android.hardware.usb.UsbManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object UsbModule {

    @Provides
    fun provideUsbManager(
        @ApplicationContext context: Context
    ): UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
}
