package jgo.compiler
package interm
package symbol

import types._

import util._

sealed abstract class Variable extends ValueSymbol

/*case class PackageVar(pkg: Package, name: String, typeOf: Type) extends Variable(typeOf) {
  override def toString = "var " + pkg.name + "." + name + ", type = " + typeOf
}*/

case class GlobalVar(name: String, typeOf: Type) extends Variable {
  override def toString = "global var " + name + ": " + typeOf
}

class LocalVar(val name: String, val typeOf: Type) extends Variable with Freezable {
  private var closedOver: Boolean = false
  
  /**
   * Records that this variable has been closed over.
   * A variable is said to be <i>closed over</i> if it is referred
   * to (read from or written to) from within the body of a closure
   * and belongs to the lexical (enclosing) scope of that closure.
   * In other words, a variable is closed over if it is declared
   * outside a certain closure but used inside it. Such variables
   * require special handling at runtime.
   */
  def setClosedOver() {
    errIfFrozen
    closedOver = true
  }
  
  /**
   * States whether or not this local variable has been referenced
   * from a closure.
   */
  def isClosedOver: Boolean = closedOver
  
  override def toString = "local var " + name + ": " + typeOf
}