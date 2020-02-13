package org.jetbrains.research.kfg.ir

import com.abdullin.kthelper.algorithm.Graph
import com.abdullin.kthelper.algorithm.GraphView
import com.abdullin.kthelper.algorithm.Viewable
import com.abdullin.kthelper.collection.queueOf
import com.abdullin.kthelper.defaultHashCode
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.builder.cfg.LabelFilterer
import org.jetbrains.research.kfg.ir.value.BlockUser
import org.jetbrains.research.kfg.ir.value.SlotTracker
import org.jetbrains.research.kfg.ir.value.UsableBlock
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.type.parseMethodDesc
import org.objectweb.asm.commons.JSRInlinerAdapter
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.ParameterNode

data class MethodDesc(val args: Array<Type>, val retval: Type) {

    companion object {
        fun fromDesc(tf: TypeFactory, desc: String): MethodDesc {
            val (args, retval) = parseMethodDesc(tf, desc)
            return MethodDesc(args, retval)
        }
    }

    val asmDesc: String
        get() = "(${args.joinToString(separator = "") { it.asmDesc }})${retval.asmDesc}"

    override fun hashCode() = defaultHashCode(*args, retval)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as MethodDesc
        return this.args.contentEquals(other.args) && this.retval == other.retval
    }

    override fun toString() = "(${args.joinToString { it.name }}): ${retval.name}"
}

class Method(cm: ClassManager, node: MethodNode, val `class`: Class)
    : Node(cm, node.name, node.access), Graph<BasicBlock>, Iterable<BasicBlock>, BlockUser, Viewable {

    companion object {
        private val CONSTRUCTOR_NAMES = arrayOf("<init>")
        private val STATIC_INIT_NAMES = arrayOf("<clinit>")
    }

    val mn: MethodNode
    val desc = MethodDesc.fromDesc(cm.type, node.desc)
    val argTypes get() = desc.args
    val returnType get() = desc.retval
    val parameters = arrayListOf<Parameter>()
    val exceptions = hashSetOf<Class>()
    val basicBlocks = arrayListOf<BasicBlock>()
    val catchEntries = hashSetOf<CatchBlock>()
    val slottracker = SlotTracker(this)

    init {
        val temp = JSRInlinerAdapter(node, node.access, node.name,
                node.desc, node.signature,
                node.exceptions?.mapNotNull { it as? String }?.toTypedArray())
        node.accept(temp)
        mn = LabelFilterer(temp).build()
        mn.parameters?.withIndex()?.forEach { (indx, param) ->
            param as ParameterNode
            parameters.add(Parameter(cm, indx, param.name, desc.args[indx], param.access))
        }

        mn.exceptions?.forEach { exceptions.add(cm.getByName(it as String)) }

        addVisibleAnnotations(@Suppress("UNCHECKED_CAST") (mn.visibleAnnotations as List<AnnotationNode>?))
        addInvisibleAnnotations(@Suppress("UNCHECKED_CAST") (mn.invisibleAnnotations as List<AnnotationNode>?))
    }

    override val entry: BasicBlock
        get() = basicBlocks.first { it is BodyBlock && it.predecessors.isEmpty() }

    val prototype: String
        get() = "$`class`::$name$desc"

    val isConstructor: Boolean
        get() = name in CONSTRUCTOR_NAMES

    val isStaticInitializer: Boolean
        get() = name in STATIC_INIT_NAMES

    val bodyBlocks: List<BasicBlock>
        get() {
            val catches = catchBlocks
            return basicBlocks.filter { it !in catches }.toList()
        }

    val catchBlocks: List<BasicBlock>
        get() {
            val catchMap = hashMapOf<BasicBlock, Boolean>()
            val result = arrayListOf<BasicBlock>()
            val queue = queueOf<BasicBlock>()
            queue.addAll(catchEntries)
            while (queue.isNotEmpty()) {
                val top = queue.poll()
                val isCatch = top.predecessors.fold(true) { acc, bb -> acc && catchMap.getOrPut(bb) { false } }
                if (isCatch) {
                    result.add(top)
                    queue.addAll(top.successors)
                    catchMap[top] = true
                }
            }
            return result
        }

    override val asmDesc
        get() = desc.asmDesc

    override val nodes: Set<BasicBlock>
        get() = basicBlocks.toSet()

    fun isEmpty() = basicBlocks.isEmpty()
    fun isNotEmpty() = !isEmpty()

    fun add(bb: BasicBlock) {
        if (!basicBlocks.contains(bb)) {
            assert(!bb.hasParent) { log.error("Block ${bb.name} already belongs to other method") }
            basicBlocks.add(bb)
            slottracker.addBlock(bb)
            bb.addUser(this)
            bb.parentUnsafe = this
        }
    }

    fun addBefore(before: BasicBlock, bb: BasicBlock) {
        if (!basicBlocks.contains(bb)) {
            assert(!bb.hasParent) { log.error("Block ${bb.name} already belongs to other method") }
            val index = basicBlocks.indexOf(before)
            assert(index >= 0) { log.error("Block ${before.name} does not belong to method $this") }

            basicBlocks.add(index, bb)
            slottracker.addBlock(bb)
            bb.addUser(this)
            bb.parentUnsafe = this
        }
    }

    fun addAfter(after: BasicBlock, bb: BasicBlock) {
        if (!basicBlocks.contains(bb)) {
            assert(!bb.hasParent) { log.error("Block ${bb.name} already belongs to other method") }
            val index = basicBlocks.indexOf(after)
            assert(index >= 0) { log.error("Block ${after.name} does not belong to method $this") }

            basicBlocks.add(index + 1, bb)
            slottracker.addBlock(bb)
            bb.addUser(this)
            bb.parentUnsafe = this
        }
    }

    fun remove(block: BasicBlock) {
        if (basicBlocks.contains(block)) {
            assert(block.parentUnsafe == this) { log.error("Block ${block.name} don't belong to $this") }
            basicBlocks.remove(block)

            if (block in catchEntries) {
                catchEntries.remove(block)
            }

            block.removeUser(this)
            block.parentUnsafe = null
        }
    }

    fun addCatchBlock(bb: CatchBlock) {
        require(bb in basicBlocks)
        catchEntries.add(bb)
    }

    fun getNext(from: BasicBlock): BasicBlock {
        val start = basicBlocks.indexOf(from)
        return basicBlocks[start + 1]
    }

    fun getBlockByLocation(location: Location) = basicBlocks.find { it.location == location }
    fun getBlockByName(name: String) = basicBlocks.find { it.name.toString() == name }

    fun print() = buildString {
        appendln(prototype)
        append(basicBlocks.joinToString(separator = "\n\n") { "$it" })
    }

    override fun toString() = prototype
    override fun iterator() = basicBlocks.iterator()

    override fun hashCode() = defaultHashCode(name, `class`, desc)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Method
        return this.name == other.name && this.`class` == other.`class` && this.desc == other.desc
    }

    override fun replaceUsesOf(from: UsableBlock, to: UsableBlock) {
        (0 until basicBlocks.size)
                .filter { basicBlocks[it] == from }
                .forEach {
                    basicBlocks[it].removeUser(this)
                    basicBlocks[it] = to.get()
                    to.addUser(this)
                }
    }

    override val graphView: List<GraphView>
        get() {
            val nodes = hashMapOf<String, GraphView>()
            nodes[name] = GraphView(name, prototype)

            basicBlocks.map { bb ->
                val label = StringBuilder()
                label.append("${bb.name}: ${bb.predecessors.joinToString(", ") { it.name.toString() }}\\l")
                bb.instructions.forEach { label.append("    ${it.print().replace("\"", "\\\"")}\\l") }
                nodes[bb.name.toString()] = GraphView(bb.name.toString(), label.toString())
            }

            if (!isAbstract) {
                val entryNode = nodes.getValue(entry.name.toString())
                nodes.getValue(name).addSuccessor(entryNode)
            }

            basicBlocks.forEach {
                val current = nodes.getValue(it.name.toString())
                for (succ in it.successors) {
                    current.addSuccessor(nodes.getValue(succ.name.toString()))
                }
            }

            return nodes.values.toList()
        }

    fun view(dot: String, viewer: String) = view(name, dot, viewer)
}