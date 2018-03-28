package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.UnexpectedException
import org.jetbrains.research.kfg.util.defaultHashCode
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.Type

sealed class ValueName
class StrName(val value: String) : ValueName() {
    override fun toString() = value
    override fun hashCode() = value.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (this.javaClass != other?.javaClass) return false
        other as StrName
        return this.value == other.value
    }
}

class Slot(private val st: SlotTracker) : ValueName() {
    fun getNumber() = st.getSlotNumber(this)
    override fun toString(): String {
        val num = this.getNumber()
        return if (num == -1) "NO_SLOT_FOR${System.identityHashCode(this)}" else "%$num"
    }
}

object UndefinedName : ValueName() {
    override fun toString(): String = throw UnexpectedException("Trying to print undefined name")
}

class SlotTracker(val method: Method) {
    private val slots = mutableMapOf<Slot, Int>()

    fun getSlotNumber(slot: Slot) = slots.getOrDefault(slot, -1)

    fun getNextSlot(): Slot {
        val slot = Slot(this)
        slots[slot] = slots.size
        return slot
    }

    fun rerun() {
        slots.clear()
        var count = 0
        for (inst in method.flatten()) {
            for (value in inst)
                if (value.name is Slot) slots.getOrPut(value.name, { count++ })
            if (inst.name is Slot) slots.getOrPut(inst.name, { count++ })
        }
    }
}

abstract class Value(val name: ValueName, val type: Type) : UsableValue {
    override val users = mutableSetOf<User>()

    constructor(name: String, type: Type) : this(StrName(name), type)

    fun isNameDefined() = name is UndefinedName
    fun hasRealName() = name is StrName
    override fun toString() = name.toString()

    override fun get() = this
}

class Argument(argName: String, val method: Method, type: Type) : Value(argName, type) {
    override fun hashCode() = defaultHashCode(name, type, method)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as Argument
        return this.name == other.name && this.type == other.type && this.method == other.method
    }
}

class ThisRef(type: Type) : Value("this", type) {
    override fun hashCode() = defaultHashCode(name, type)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as ThisRef
        return this.type == other.type
    }
}