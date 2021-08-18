package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.UsageContext
import org.jetbrains.research.kfg.type.Type

class JumpInst internal constructor(type: Type, successor: BasicBlock, ctx: UsageContext) :
    TerminateInst(UndefinedName(), type, arrayOf(), arrayOf(successor), ctx) {

    val successor: BasicBlock
        get() = succs[0]

    override fun print() = "goto ${successor.name}"
    override fun clone(ctx: UsageContext): Instruction = JumpInst(type, successor, ctx)
}