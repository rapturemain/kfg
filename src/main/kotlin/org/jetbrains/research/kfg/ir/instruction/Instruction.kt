package org.jetbrains.research.kfg.ir.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.value.Value

abstract class Instruction (val operands: Array<Value>) {
    var bb : BasicBlock? = null
}