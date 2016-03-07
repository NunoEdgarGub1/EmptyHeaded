package duncecap

import duncecap.attr._

import scala.collection.immutable.TreeSet
import scala.collection.mutable.ListBuffer

case class IR(val rules:List[Rule]) {
  def this(){this(List())}
  def getNumRules():Int = {rules.length}
  def getRule(i:Int):Rule = {rules(i)}
}

case class Rule(val result:Result,
                val recursion:Option[Recursion],
                val operation:Operation,
                val order:Order,
                val project:Project,
                val join:Join,
                val aggregations:Aggregations,
                val filters:Filters) {
  def getResult():Result = {result}
  def getRecursion():Option[Recursion] = {recursion}
  def getOperation():Operation = {operation}
  def getOrder():Order = {order}
  def getProject():Project = {project}
  def getJoin():Join = {join}
  def getFilters():Filters = {filters}
  def getAggregations():Aggregations = {aggregations}
  def attrNameAgnosticEquals(otherRule:Rule):Boolean = {
    if (join.rels.size != otherRule.join.rels.size) return false
    else {
      val joinAggregates1 = getAggregations().values.flatMap(agg => {
        val attrs = agg.attrs.values
        attrs.map(attr => { (attr, agg) })
      }).toMap
      val joinAggregates2 = otherRule.getAggregations().values.flatMap(agg => {
        val attrs = agg.attrs.values
        attrs.map(attr => { (attr, agg) })
      }).toMap

      val attrSet1 = join.rels.foldLeft(TreeSet[String]())(
        (accum: TreeSet[String], rel:Rel) => accum | TreeSet[String](rel.attrs.values: _*))
      val attrSet2 = otherRule.join.rels.foldLeft(TreeSet[String]())(
        (accum: TreeSet[String], rel:Rel) => accum | TreeSet[String](rel.attrs.values: _*))
      val attrToSelection1:Map[Attr,Array[Selection]]
        = attrSet1.map(attr => (attr, PlanUtil.getSelection(attr, filters.values.toArray))).toMap
      val attrToSelection2:Map[Attr,Array[Selection]]
        = attrSet2.map(attr => (attr, PlanUtil.getSelection(attr, otherRule.filters.values.toArray))).toMap

      return matchRelations(
        this.join.rels.toSet,
        otherRule.join.rels.toSet,
        this.result.rel,
        otherRule.result.rel,
        Map[Attr, Attr](),
        attrToSelection1,
        attrToSelection2,
        joinAggregates1,
        joinAggregates2)
    }
  }


  private def matchRelations(rule1Join:Set[Rel],
                             rule2Join:Set[Rel],
                             rule1Output:Rel,
                             rule2Output:Rel,
                             attrMap:Map[Attr, Attr],
                             attrToSelection1: Map[Attr, Array[Selection]],
                             attrToSelection2: Map[Attr, Array[Selection]],
                             joinAggregates1:Map[String, Aggregation],
                             joinAggregates2:Map[String, Aggregation]): Boolean = {
    if (rule1Join.isEmpty && rule2Join.isEmpty) return true

    val matches = rule2Join.map(otherRel => (rule2Join-otherRel, // what does this do?
      PlanUtil.attrNameAgnosticRelationEquals(
        rule1Output,
        rule1Join.head,
        rule2Output,
        otherRel,
        attrMap,
        attrToSelection1,
        attrToSelection2,
        joinAggregates1,
        joinAggregates2))).filter(m => {
      m._2.isDefined
    })
    return matches.exists(m => {
      matchRelations(rule1Join.tail, m._1, rule1Output, rule2Output, m._2.get, attrToSelection1, attrToSelection2, joinAggregates1, joinAggregates2)
    })
  }
}

case class Attributes(val values:List[String])

case class Annotations(val values:List[String])


abstract trait RelBase {
  val name:String
  val attrs:Attributes
  val anno:Annotations
}

case class Rel(
  override val name:String,
  override val attrs:Attributes,
  override val anno:Annotations=Annotations(List())) extends RelBase {
  def getName():String = {name}
  def getAttributes():Array[String] = {attrs.values.toArray}
  def getAnnotations():Array[String] = {anno.values.toArray}
}

case class Result(val rel:Rel, val isIntermediate:Boolean){
  def getRel():Rel = { rel }
  def getIsIntermediate():Boolean = { isIntermediate }
}

abstract class ConvergenceCriteria {}

case class ITERATIONS() extends ConvergenceCriteria {}
case class EPSILON() extends ConvergenceCriteria {}

case class Recursion(
  val criteria:ConvergenceCriteria,
  val operation:Op,
  val value:String) {
  def getCriteria():String = {
    criteria match {
      case i:ITERATIONS => "iterations"
      case _ => throw new Exception("Convergence criterion not supported.")
    }
  }
  def getOperation():String = {operation.value}
  def getValue():String = {value}
}

case class Order(val attrs:Attributes){
  def getAttributes():Array[String] = { attrs.values.toArray }
}

case class Project(val attrs:Attributes){
  def getAttributes():Array[String] = { attrs.values.toArray }
}

case class Operation(val value:String){
  def getOperation():String = {value}
}

case class Join(val rels:List[Rel]){
  def getNumRels():Int = { rels.length }
  def getRel(i:Int):Rel = { rels(i) }

  override def equals(that:Any):Boolean = {
    that match {
      case that: Join => rels.toSet == that.rels.toSet
      case _ => false
    }
  }

  override def hashCode():Int = {
    rels.toSet.hashCode()
  }
}

case class Selection(val attr:String,
                     val operation:Op,
                     val value:String) {
  def getAttr():String = { attr }
  def getOperation():String = { operation.value }
  def getValue():String = { value }
}

abstract class Op {
  val value:String = ""
}

//FIXME: ADD MORE OPS
case class EQUALS() extends Op {
  override val value = "="
}

case class Filters(val values:List[Selection]){
  def getNumFilters():Int = {values.length}
  def getSelect(i:Int):Selection = {values(i)}
}

abstract class AggOp {
  val value:String = ""
}

//FIXME: ADD MORE OPS
case class SUM() extends AggOp {
  override val value = "+"
}
case class CONST() extends AggOp {
  override val value = ""
}
case class MIN() extends AggOp {
  override val value = "<"
}

case class Aggregation(
  val annotation:String,
  val datatype:String,
  val operation:AggOp,
  val attrs:Attributes,
  val init:String,
  val expression:String,
  val usedScalars:List[Rel])

case class Aggregations(val values:List[Aggregation]){
  def getNumAggregations():Int = {values.length}
  def getAnnotation(i:Int):String = {values(i).annotation}
  def getDatatype(i:Int):String = {values(i).datatype}
  def getOperation(i:Int):String = {values(i).operation.value}
  def getAttributes(i:Int):Array[String] = {values(i).attrs.values.toArray}
  def getInit(i:Int):String = {values(i).init}
  def getExpression(i:Int):String = {values(i).expression}
  def getDependedOnRels(i:Int):Array[Rel] = {values(i).usedScalars.toArray}
}

///////////////////////////////////////////////////////////
//From here down is just helper methods to bind to python.
//Should not need to use unless you are modifying the python
//jpype bindings.
///////////////////////////////////////////////////////////
object IR {
  //Special builder to convert arrays to lists & check
  //that everything is consistent with what is in DB
  def buildRel(name:String,
               attrs:Array[String],
               anno:Array[String]) : Rel = {
    return Rel(name,
      Attributes(attrs.toList),
      Annotations(anno.toList))
  }

  def buildAttributes(attrs:Array[String]): Attributes = {
    return Attributes(attrs.toList)
  }
}

class IRBuilder() {
  val rules = ListBuffer[Rule]()

  def addRule(rule:Rule) {
    rules += rule
  }

  def addRules(rule:List[Rule]) {
    rules ++= rule
  }

  def build():IR = {
    IR(rules.toList)
  }
}

class AggregationsBuilder() {
  val aggregations = ListBuffer[Aggregation]()

  def getOp(op:String) : AggOp = {
    op match {
      case "SUM" => SUM()
      case "CONST" => CONST()
      case "MIN" => MIN()
      case _ =>
        throw new Exception("Aggregation operation " + op + " not supported.")
    }
  }

  def addAggregation(
    annotation:String,
    datatype:String,
    operation:String,
    attrs:Array[String],
    init:String,
    expression:String,
    dependsOn:List[Rel]) {
    aggregations += Aggregation(
      annotation,
      datatype:String,
      getOp(operation),
      Attributes(attrs.toList),
      init,
      expression,
      dependsOn)
  }

  def build():Aggregations = {
    Aggregations(aggregations.toList)
  }
}

class FilterBuilder() {
  val filters = ListBuffer[Selection]()

  private def getOp(op:String) : Op = {
    op match {
      case "=" => EQUALS()
      case _ =>
        throw new Exception("Selection operation " + op + " not supported.")
    }
  }

  def buildSelection(attr:String,operation:String,value:String):Selection = {
    Selection(attr,getOp(operation),value)
  }

  def addSelection(sel:Selection) {
    filters += sel
  }

  def build():Filters = {
    Filters(filters.toList)
  }
}

class JoinBuilder() {
  val joins = ListBuffer[Rel]()

  def addRel(name:String,attrs:Array[String],anno:Array[String]) {
    joins += Rel(name,Attributes(attrs.toList),Annotations(anno.toList))
  }

  def build():Join = {
    Join(joins.toList)
  }
}

class RecursionBuilder() {
  def build(criteria:String,op:String,value:String):Option[Recursion] = {
    (criteria,op,value) match {
      case ("","","") => None
      case ("iterations",o,v) => {
        val operation = o match {
          case "=" => EQUALS()
          case _ => throw new Exception("Not supported.")
        }
        Some(Recursion(ITERATIONS(),operation,v))
      }
      case _ => throw new Exception("Not supported.")
    }
  }
}
