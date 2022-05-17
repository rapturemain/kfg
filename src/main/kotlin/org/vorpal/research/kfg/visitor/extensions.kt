package org.vorpal.research.kfg.visitor

import org.vorpal.research.kfg.ir.Node
import org.vorpal.research.kfg.visitor.pass.AnalysisResult
import org.vorpal.research.kfg.visitor.pass.AnalysisVisitor

inline fun <reified Dependency : NodeVisitor> NodeVisitor.addRequiredPass() {
    this.pipeline.visitorRegistry.addRequiredPass(this::class.java, Dependency::class.java)
}

inline fun <reified Dependency : NodeVisitor> NodeVisitor.addSoftDependencyPass() {
    this.pipeline.visitorRegistry.addSoftDependencyPass(this::class.java, Dependency::class.java)
}

inline fun <reified Dependency : KfgProvider<*>> NodeVisitor.addRequiredProvider() {
    this.pipeline.visitorRegistry.addRequiresProvider(this::class.java, Dependency::class.java)
}

inline fun <reified Dependency : AnalysisVisitor<*>> NodeVisitor.addRequiredAnalysis() {
    this.pipeline.visitorRegistry.addRequiredAnalysis(this::class.java, Dependency::class.java)
}

inline fun <reified Dependency : AnalysisVisitor<*>> NodeVisitor.addPersistedAnalysis() {
    this.pipeline.visitorRegistry.addPersistedAnalysis(this::class.java, Dependency::class.java)
}

inline fun <reified Visitor : NodeVisitor> Pipeline.schedule() {
    this.schedule(Visitor::class.java)
}

inline fun <reified Provider : KfgProvider<DataType>, DataType> NodeVisitor.getProvider(): KfgProvider<DataType> {
    return this.pipeline.visitorRegistry.getProvider(Provider::class.java) as KfgProvider<DataType>
}

inline fun <reified Analysis : AnalysisVisitor<DataType>, DataType : AnalysisResult> NodeVisitor.getAnalysis(node: Node): DataType {
    return this.pipeline.analysisManager.getAnalysisResult(Analysis::class.java, node)
}

inline fun <reified Provider : KfgProvider<DataType>, DataType> Pipeline.getProvider(): KfgProvider<DataType> {
    return this.visitorRegistry.getProvider(Provider::class.java) as KfgProvider<DataType>
}

inline fun <reified Analysis : AnalysisVisitor<DataType>, DataType : AnalysisResult> Pipeline.getAnalysis(node: Node): DataType {
    return this.analysisManager.getAnalysisResult(Analysis::class.java, node)
}

fun NodeVisitor.invalidateAnalysisCache(node: Node, persistedAdditional: List<Class<out AnalysisVisitor<*>>> = emptyList()) {
    this.pipeline.analysisManager.invalidateAllExcept(this::class.java, node, persistedAdditional)
}

interface VisitorWrapper {
    val wrapped: NodeVisitor
}