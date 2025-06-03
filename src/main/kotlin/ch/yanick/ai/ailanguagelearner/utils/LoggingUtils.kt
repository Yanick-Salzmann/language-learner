package ch.yanick.ai.ailanguagelearner.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory


class LoggerDelegate {
    operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): Logger =
        LoggerFactory.getLogger(thisRef?.javaClass)
}


fun logger() = LoggerDelegate()