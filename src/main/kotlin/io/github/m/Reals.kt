package io.github.m

/**
 * M real definitions.
 */
@Suppress("unused")
object Reals {
    @MField("real.+")
    @JvmField
    val add: Value = Function { x, y -> Real((x as Real).value + (y as Real).value) }

    @MField("real.-")
    @JvmField
    val sub: Value = Function { x, y -> Real((x as Real).value - (y as Real).value) }

    @MField("real.*")
    @JvmField
    val mul: Value = Function { x, y -> Real((x as Real).value * (y as Real).value) }

    @MField("real./")
    @JvmField
    val div: Value = Function { x, y -> Real((x as Real).value / (y as Real).value) }

    @MField("real.%")
    @JvmField
    val rem: Value = Function { x, y -> Real((x as Real).value % (y as Real).value) }

    @MField("real.<")
    @JvmField
    val lt: Value = Function { x, y -> Bool((x as Real).value < (y as Real).value) }

    @MField("real.>")
    @JvmField
    val gt: Value = Function { x, y -> Bool((x as Real).value > (y as Real).value) }

    @MField("real.=")
    @JvmField
    val eq: Value = Function { x, y -> Bool((x as Real).value == (y as Real).value) }
}