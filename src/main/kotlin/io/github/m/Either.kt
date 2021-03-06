package io.github.m

/**
 * M wrapper class for either.
 */
sealed class Either : Value {
    /**
     * The left value of either.
     */
    data class Left(val value: Value) : Either() {
        override fun invoke(arg: Value) = Value { arg2 -> this(arg, arg2) }
        override fun invoke(arg1: Value, arg2: Value) = arg1(value)
    }

    /**
     * The right value of either.
     */
    data class Right(val value: Value) : Either() {
        override fun invoke(arg: Value) = Value { arg2 -> this(arg, arg2) }
        override fun invoke(arg1: Value, arg2: Value) = arg2(value)
    }

    companion object {
        fun from(value: Value) = value as? Either ?: value(Value(::Left), Value(::Right)) as Either
    }

    /**
     * M either definitions.
     */
    @Suppress("unused")
    object Definitions {
        @MField("left")
        @JvmField
        val left: Value = Value { x -> Left(x) }

        @MField("right")
        @JvmField
        val right: Value = Value { x -> Right(x) }

        @MField("left?")
        @JvmField
        val isLeft: Value = Value { x -> Bool(from(x) is Left) }

        @MField("right?")
        @JvmField
        val isRight: Value = Value { x -> Bool(from(x) is Right) }
    }
}