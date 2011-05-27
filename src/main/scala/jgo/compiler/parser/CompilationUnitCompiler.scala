package jgo.compiler
package parser

import funcs._
import stmts._
import scoped._

import lexer._
import scope._
import interm._
import interm.types._
import symbol._
import codeseq._

import scala.collection.{mutable => mut}

class CompilationUnitCompiler(target: Package = Package("main"), in: Input) extends Declarations with GrowablyScoped {
  def growable = SequentialScope.base(UniverseScope)
  def scope = growable
  
  private[this] val functionCompilers: mut.Map[Function, FunctionCompiler] = mut.Map.empty
  private[this] var globalVars: List[GlobalVar] = Nil
  
  protected def mkVariable(name: String, typeOf: Type) = {
    val v = new GlobalVar(name, typeOf)
    globalVars ::= v
    (v, CodeBuilder.empty)
  }
  
  val initCodeM = extractFromParseResult(parseFile(in)) map {
    _.foldLeft(CodeBuilder.empty)(_ |+| _).result 
  }
  
  lazy val compile: M[PkgInterm] = {
    val functionsM = {
      val functionMap = mut.Map[Function, FunctionInterm]()
      var errors: M[Any] = Result(())
      for ((f, fCompl) <- functionCompilers) {
        val fIntermM = fCompl.compile
        errors = errors then fIntermM
        for (fInterm <- fIntermM)
          functionMap.put(f, fInterm)
      }
      for (_ <- errors) yield
        functionMap.toMap
    }
    //initCodeM holds all of the top-level errors, if any;
    //functionsM holds all of the function-level ones
    for ((initCode, functions) <- (initCodeM, functionsM)) yield
      PkgInterm(target, globalVars, initCode, functions)
  }
  
  
  private lazy val parseFile: PM[List[CodeBuilder]] =                  "file" $
    repWithSemi(topLevelDecl)
  
  private lazy val topLevelDecl: PM[CodeBuilder] =    "top level declaration" $
    (declaration | function)
  
  private lazy val function: PM[CodeBuilder] =                "function decl" $
    "func" ~! ident ~ signature ~ inputAt("{") <~ skipBlock  ^^ { case pos ~ name ~ sigM ~ in =>
      sigM flatMap { sig =>
        val funcCompl = new FunctionCompiler(name, sig, scope, in)
        functionCompilers.put(funcCompl.target, funcCompl)
        //add to the scope
        bind(name, funcCompl.target)(pos) map { _ => CodeBuilder.empty } //always empty code builder, req'd for compat.
      }
    }
  
  private lazy val skipBlock = Parser { in =>
    var cur = in
    var level = 0
    do {
      if (cur.first == Keyword("{"))
        level += 1
      else if (cur.first == Keyword("}"))
        level -= 1
    } while (level > 0)
    Success((), cur.rest)
  }
}