// TypeChecking.java
//

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.util.*;
import java.lang.Class;

public class TypeChecking {

	// Checks whether a value of type srcTyp can be assigned to or compared with
	// a value/variable of type destType; argument ctx is used only for line number info
	// Assignability is defined in the Go specification:
	//   https://golang.org/ref/spec#Assignability
	// This code is incomplete because it does not handle assignment of nil to pointer variables,
	// and it should handle multiple assignment.
	// Note that this method should make use of the identicalTypes method defined below.
	public static boolean checkAssignability(Type destTyp, Type srcTyp, ParserRuleContext ctx) {
		// handle the obvious case!
		if (destTyp == srcTyp) return true;
		// avoid error messages when type is unknown, allow arbitrary dest types
		// for some of the methiods in imported packages
		if (destTyp == Type.unknownType || destTyp == Type.anyType)
		    return true;
		if (srcTyp == Type.unknownType) return true;

		assert srcTyp != null;
		assert destTyp != null;

		// handle case when src is an untyped numeric constant
		if (srcTyp instanceof Type.UntypedNumber) {
			if (destTyp instanceof Type.Flt) return true;
			if (!(destTyp instanceof Type.Int || destTyp instanceof Type.Uint))
				return false;
			// should check whether the number will fit into the particular size
			// of int provided for destTyp
			return ((Type.UntypedNumber)srcTyp).isInteger();
		}

		// handle initialization of an array or slice
		// Note: this code should be expanded to also handle the destination
		// being a TypeList too ... that's needed for multiple assignment as in
		//      str, x = "abc", 26
		if (srcTyp instanceof Type.TypeList) {
			if (destTyp instanceof Type.Array || destTyp instanceof Type.Slice) {
				// check element types
				Type et = destTyp instanceof Type.Array?
						((Type.Array)destTyp).getElementType() :
						((Type.Slice)destTyp).getElementType() ;
				for( Type t : ((Type.TypeList)srcTyp).getTypes() ) {
					if (!checkAssignability(et,t,ctx)) return false;
				}
				return true;
			}
		}

		ReportError.error(ctx, "type "+srcTyp.toString()+" is incompatible with "+destTyp.toString());
		return false;
	}

	// Checks if the function with signature fntyp can be called with the argument
	// types actualTypes. The result is the function result type.
	// This code is believed to be complete!
    public static Type checkFunctionCall( Type.Function fntyp, Type[] actualTypes, ParserRuleContext ctx ) {
        Type[] formalTypes = fntyp.getParameters();
        int i = 0;
        for( int k = 0; k<actualTypes.length; k++ ) {
            if (i >= formalTypes.length) {
                ReportError.error(ctx, "too many arguments in function call");
                break;
            }
            if (formalTypes[i] == Type.variadicAnyType)
                break;  // no need to check further
            checkAssignability(formalTypes[i], actualTypes[k], ctx);
            i++;
        }
        if (actualTypes.length < formalTypes.length && formalTypes[i] != Type.variadicAnyType)
            ReportError.error(ctx, "too few arguments in function call");
		Type[] restyp = fntyp.getResults();
		if (restyp.length == 0) return Type.voidType;
		assert restyp[0] != null;
		return restyp[0];
	}

 	// This function tests if two types have the same underlying types
 	// as explained in the Go specification:
 	//    https://golang.org/ref/spec#Types
 	public static boolean sameUnderlyingTypes( Type a, Type b ) {
 		if (a.getClass() != b.getClass()) return false;
 		if (a == b) return true;
 		if (a instanceof Type.Array) {
 			Type.Array aa = (Type.Array)a;
 			Type.Array bb = (Type.Array)b;
 			return aa.getElementType() == bb.getElementType();
 		}
 		if (a instanceof Type.Slice) {
 			Type.Slice aa = (Type.Slice)a;
 			Type.Slice bb = (Type.Slice)b;
 			return aa.getElementType() == bb.getElementType();
 		}
 		if (a instanceof Type.Pointer) {
 			Type.Pointer aa = (Type.Pointer)a;
 			Type.Pointer bb = (Type.Pointer)b;
 			return aa.getBaseType() == bb.getBaseType();
 		}
 		if (a instanceof Type.Pointer) {
 			Type.Pointer aa = (Type.Pointer)a;
 			Type.Pointer bb = (Type.Pointer)b;
 			return aa.getBaseType() == bb.getBaseType();
 		}
 		// does that cover all the cases?
 		return false;
 	}

 	// This tests for Type Identity as described in the Go specification:
 	//    https://golang.org/ref/spec#Type_identity
 	public static boolean identicalTypes( Type a, Type b ) {
 		if (a.isNamedType()) {
 			// if two types are named differently, they are not identical!
 			if (b.isNamedType())
 				return a.getName().equals(b.getName());
 			return false;
 		}
 		if (b.isNamedType())
 			return false;
 		if (a.getClass() != b.getClass()) return false;
 		if (a instanceof Type.Array) {
 			Type.Array aa = (Type.Array)a;
 			Type.Array bb = (Type.Array)b;
 			return (aa.getSize() == bb.getSize()) &&
 				identicalTypes(aa.getElementType(), bb.getElementType());
 		}
 		if (a instanceof Type.Slice) {
 			Type.Slice aa = (Type.Slice)a;
 			Type.Slice bb = (Type.Slice)b;
 			return identicalTypes(aa.getElementType(), bb.getElementType());
 		}
 		if (a instanceof Type.Pointer) {
 			Type.Pointer aa = (Type.Pointer)a;
 			Type.Pointer bb = (Type.Pointer)b;
 			return identicalTypes(aa.getBaseType(), bb.getBaseType());
 		}
 		if (a instanceof Type.Struct) {
 			Type.Struct aa = (Type.Struct)a;
 			Type.Struct bb = (Type.Struct)b;
 			LinkedHashMap<String, Symbol> af = aa.getFields();
 			LinkedHashMap<String, Symbol> bf = bb.getFields();
 			if (af.size() != bf.size())
 				return false;
 			Iterator<Symbol> av = af.values().iterator();
 			Iterator<Symbol> bv = bf.values().iterator();
 			while(av.hasNext()) {
 				Symbol as = (Symbol)av.next();
 				Symbol bs = (Symbol)bv.next();
 				if (!as.getName().equals(bs.getName()))
 					return false;
 				if (!identicalTypes(as.getType(), bs.getType()))
 					return false;
 			}
 			return true;
 		}
 		if (a instanceof Type.Function) {
 			Type.Function aa = (Type.Function)a;
 			Type.Function bb = (Type.Function)b;
 			Type[] ap = aa.getParameters();
 			Type[] bp = bb.getParameters();
 			if (ap.length != bp.length) return false;
 			for(int i=0; i<ap.length; i++) {
 				if (!identicalTypes(ap[i], bp[i])) return false;
 			}
 			ap = aa.getResults();
 			bp = bb.getResults();
 			if (ap.length != bp.length) return false;
 			for(int i=0; i<ap.length; i++) {
 				if (!identicalTypes(ap[i], bp[i])) return false;
 			}
 			return true;
 		}
 		// did we cover everything?
 		return false;
 	}


 	// Report an error if the operator is not applicable to the operand types;
 	// return the type of the result
    public static Type checkBinOp(Type lhs, Type rhs, String op, ParserRuleContext ctx) {
      // binary_op  = "||" | "&&" | rel_op | add_op | mul_op .
      // rel_op     = "==" | "!=" | "<" | "<=" | ">" | ">=" .
      // add_op     = "+" | "-" | "|" | "^" .
      // mul_op     = "*" | "/" | "%" | "<<" | ">>" | "&" | "&^" .

      // Named types must match (strict type checking)
      if (lhs.isNamedType() && rhs.isNamedType()) {
         if (!identicalTypes(lhs, rhs)) {
           ReportError.error(ctx, "Mismatch type: " + lhs.getName() + " " + op + " "+ rhs.getName());
         }
      }


      // if types are NAMED, the name must be the same
      switch(op) {
        // Or
        case "||":
        break;

        // And
        case "&&":
        break;

        // Relational Operators "==" | "!=" | "<" | "<=" | ">" | ">=" .
        case "==":
        break;

        case "!=":
        break;

        case "<":
        break;

        case "<=":
        break;

        case ">":
        break;

        case ">=":
        break;


        // +    sum                    integers, floats, complex values, strings
        // -    difference             integers, floats, complex values
        // *    product                integers, floats, complex values
        // /    quotient               integers, floats, complex values
        // %    remainder              integers
        //
        // &    bitwise AND            integers
        // |    bitwise OR             integers
        // ^    bitwise XOR            integers
        // &^   bit clear (AND NOT)    integers
        //
        // <<   left shift             integer << unsigned integer
        // >>   right shift            integer >> unsigned integer


        // Addition Operators "+" | "-" | "|" | "^" .
        case "+":
          System.out.println(rhs.isComplete());
          if (!(lhs instanceof Type.UntypedNumber && rhs instanceof Type.UntypedNumber)) {
            if (lhs.name != "string" || rhs.name != "string") {
              ReportError.error(ctx, "Can only use " + op + " on numbers or strings.");
            }
          }
        break;

        //
        case "-":
        case "*":
        case "/":
          if (!(lhs instanceof Type.UntypedNumber && rhs instanceof Type.UntypedNumber)) {
            ReportError.error(ctx, "Can only use " + op + " on numbers.");
          }
        break;

        // Can only shift ints by uint
        case "<<":
        case ">>":
          if (rhs instanceof Type.Uint) {
            if (!(lhs instanceof Type.UntypedNumber)) {
                ReportError.error(ctx, "Can only bitshift integers.");
            } else {
              if (!((Type.UntypedNumber)lhs).isInteger()) {
                ReportError.error(ctx, "Can only bitshift integers.");
              }
            }
          } else {
            ReportError.error(ctx, "Can only bitshift by unsigned ints.");
          }
        break;

        // Can only operate with left and right hand sides being integers
        case "|":
        case "%":
        case "&":
        case "&^":
        case "^":
          if (lhs instanceof Type.UntypedNumber && rhs instanceof Type.UntypedNumber) {
            if (!((Type.UntypedNumber)lhs).isInteger() || !((Type.UntypedNumber)rhs).isInteger()) {
              ReportError.error(ctx, "Can use" + op + " on integers.");
            }
          } else {
            ReportError.error(ctx, "Can use " + op + " on integers.");
          }
        break;
      }

      // ReportError.error(ctx, lhs.toString());
      // assert false;
    	// return Type.unknownType;
      return new Type.Int(32);
    }

 	// Report an error if the operator is not applicable to the operand type;
 	// return the type of the result
    public static Type checkUnaryOp(Type opnd, String op, ParserRuleContext ctx) {
    	// very much code is missing here!
    	// unaryOp:   '+' | '-' | '!' | '^' | '*' | '&' ;

      // ReportError.error(ctx, op);
    	return Type.unknownType;
    }

}

// binary_op  = "||" | "&&" | rel_op | add_op | mul_op .
// rel_op     = "==" | "!=" | "<" | "<=" | ">" | ">=" .
// add_op     = "+" | "-" | "|" | "^" .
// mul_op     = "*" | "/" | "%" | "<<" | ">>" | "&" | "&^" .
// unary_op   = "+" | "-" | "!" | "^" | "*" | "&" | "<-" .
