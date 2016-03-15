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

    // Test cast of nil to pointer



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

      // Handle Structs
      if (destTyp instanceof Type.Struct) {
        // check element types
        LinkedList<Type> types = new LinkedList<Type>();
        for (Map.Entry<String, Symbol> entry : ((Type.Struct)destTyp).getFields().entrySet()) {
          types.add(entry.getValue().type);
        }
        int cnt = 0;
        for( Type t : ((Type.TypeList)srcTyp).getTypes() ) {
          if (!checkAssignability(types.get(cnt), t, ctx)) {
            return false;
          }
          cnt += 1;
        }
        return true;
      }
		}

    // Pointers
    if (srcTyp instanceof Type.Pointer) {
      // Allow pointer <- nil assignment
      if (srcTyp.toString() == "*(any)") {
        return true;
      }

      // Allow pointer -> pointer assingment
      if (destTyp instanceof Type.Pointer) {
        return true;  // Type.newPointerType(destTyp.baseType);
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

  // Method for doing stuff.
  public static Type GetUnderlyingType(Type x) {
    if (x instanceof Type.Pointer) {
      return ((Type.Pointer)x).getBaseType();
    }
    if (x instanceof Type.Slice) {
      return ((Type.Slice)x).getElementType();
    }
    if (x instanceof Type.Array) {
      return ((Type.Array)x).getElementType();
    }
    return x;
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

  // Take the type that is higher. IE: int , int -> int.
  //                                   int , flt -> flt
  public static Type HigherType(Type lhs, Type rhs) {
    // Take named types (highest)
    if (lhs.isNamedType()) {
      return lhs;
    }
    if (rhs.isNamedType()) {
      return rhs;
    }

    // Take floats next
    if (lhs instanceof Type.Flt) {
      return lhs;
    }
    if (rhs instanceof Type.Flt) {
      return rhs;
    }

    // Take Ints last
    if (lhs instanceof Type.Int) {
      return lhs;
    }
    return null;
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
           return Type.unknownType;
         }
      }

      // For ease latter.
      boolean lhsNumber = (GetUnderlyingType(lhs) instanceof Type.UntypedNumber ||
                           GetUnderlyingType(lhs) instanceof Type.Uint ||
                           GetUnderlyingType(lhs) instanceof Type.Int ||
                           GetUnderlyingType(lhs) instanceof Type.Flt );

      boolean rhsNumber = (GetUnderlyingType(rhs) instanceof Type.UntypedNumber ||
                           GetUnderlyingType(rhs) instanceof Type.Uint ||
                           GetUnderlyingType(rhs) instanceof Type.Int ||
                           GetUnderlyingType(rhs) instanceof Type.Flt );


      // if types are NAMED, the name must be the same
      switch(op) {
        case "||":
        case "&&":
          if (lhs.name != "bool" || rhs.name != "bool") {
            ReportError.error(ctx, "Can only use " + op + " binary op on booleans.");
            return Type.unknownType;
          }
          return Type.boolType;

        // Relational Operators "==" | "!=" | "<" | "<=" | ">" | ">=" .
        // Type x & y are numbers or
        // Type x & y are strings or
        // Type x & y are numbers
        case "==":
        case "!=":
        case "<":
        case "<=":
        case ">":
        case ">=":
          if (!(lhsNumber && rhsNumber)) {
            if (GetUnderlyingType(lhs).name != "string" || GetUnderlyingType(rhs).name != "string") {
              if (lhs.name != rhs.name) {
                ReportError.error(ctx, "Can only use relational operators on equivalent types.");
                return Type.unknownType;
              }
            }
          }
          return Type.boolType;  // Returns a bool type.


        // +    sum                    integers, floats, complex values, strings
        case "+":
          if (!(lhsNumber && rhsNumber)) {
            if (GetUnderlyingType(lhs).name != "string" || GetUnderlyingType(rhs).name != "string") {
              ReportError.error(ctx, "Can only use " + op + " on numbers or strings.");
              return Type.unknownType;
            } else {
              return lhs;  // string + string -> string.
            }
          } else {
            Type t = HigherType(lhs, rhs);
            if (t == null) {
              ReportError.error(ctx, "Bad Integer.");
              return Type.unknownType;
            }
            return t;
          }

        // Other ops. On numbers only.
        // -    difference             integers, floats, complex values
        // *    product                integers, floats, complex values
        // /    quotient               integers, floats, complex values
        case "-":
        case "*":
        case "/":
          if (!(lhsNumber && rhsNumber)) {
            ReportError.error(ctx, "Can only use " + op + " on numbers.");
            return Type.unknownType;
          }
          Type t = HigherType(lhs, rhs);
          if (t == null) {
            ReportError.error(ctx, "Bad Integer.");
            return Type.unknownType;
          }
          return t;

        // Can only shift ints by uint
        // <<   left shift             integer << unsigned integer
        // >>   right shift            integer >> unsigned integer
        case "<<":
        case ">>":
          if (GetUnderlyingType(rhs) instanceof Type.Uint) {
            if (!(lhs instanceof Type.UntypedNumber)) {
                ReportError.error(ctx, "Can only bitshift integers.");
                return Type.unknownType;
            } else {
              if (!((Type.UntypedNumber)GetUnderlyingType(lhs)).isInteger()) {
                ReportError.error(ctx, "Can only bitshift integers.");
                return Type.unknownType;
              }
            }
          } else {
            ReportError.error(ctx, "Can only bitshift by unsigned ints.");
            return Type.unknownType;
          }
          return lhs;  // Out <- In

        // Can only operate with left and right hand sides being integers
        // &    bitwise AND            integers
        // %    remainder              integers
        // |    bitwise OR             integers
        // ^    bitwise XOR            integers
        // &^   bit clear (AND NOT)    integers
        case "|":
        case "%":
        case "&":
        case "&^":
        case "^":
          if (GetUnderlyingType(lhs) instanceof Type.UntypedNumber && GetUnderlyingType(rhs) instanceof Type.UntypedNumber) {
            if (!((Type.UntypedNumber)GetUnderlyingType(lhs)).isInteger() || !((Type.UntypedNumber)GetUnderlyingType(rhs)).isInteger()) {
              ReportError.error(ctx, "Can use" + op + " on integers.");
              return Type.unknownType;
            }
          } else {
            ReportError.error(ctx, "Can use " + op + " on integers.");
            return Type.unknownType;
          }
          return lhs;  // Uut <- in

        default:
          ReportError.error(ctx, "Unknown operator " + op);
          return Type.unknownType;  // Bad!
      }
    }

 	// Report an error if the operator is not applicable to the operand type;
 	// return the type of the result
    public static Type checkUnaryOp(Type opnd, String op, ParserRuleContext ctx, Symbol sym) {
    	// very much code is missing here!
    	// unaryOp:   '+' | '-' | '!' | '^' | '*' | '&' ;

      switch (op) {
        // Only work with integers
        case("!") :
        case("+") :
        case("-") :
        case("^") :
          if (opnd instanceof Type.UntypedNumber ||
              opnd instanceof Type.Uint ||
              opnd instanceof Type.Int ||
              opnd instanceof Type.Flt) {
            return opnd;
          } else {
            ReportError.error(ctx, "");
            return Type.unknownType;
          }

        case("*") :
          if (opnd instanceof Type.Pointer) {
            return ((Type.Pointer)opnd).getBaseType();
          } else {
            ReportError.error(ctx, "Can only derefernce pointers.");
            return Type.unknownType;  // Bad!
          }

        case("&") :
          if (sym != null) {
            return Type.newPointerType(opnd);
          } else {
            ReportError.error(ctx, "Can only reference variables.");
            return Type.unknownType;
          }


        default:
          ReportError.error(ctx, "Unknown operator " + op);
          return Type.unknownType;  // Bad!
      }
    }

// END MAIN
}
