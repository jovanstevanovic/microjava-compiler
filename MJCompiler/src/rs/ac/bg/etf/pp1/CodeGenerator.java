package rs.ac.bg.etf.pp1;

import java.util.*;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.structure.SymbolDataStructure;

public class CodeGenerator extends VisitorAdaptor {
	
	private class DeclaredVariable {
		private String varName;
		private Object varValue;
		
		DeclaredVariable(String varName, Object varValue) {
			this.varName = varName;
			this.varValue = varValue;
		}
	}
	
	private Obj find(String name) {
		for (Obj o : listOfLocalSymbols)
			if (o.getName().equals(name))
				return o;
		for (Obj o : programObj.getLocalSymbols())
			if (o.getName().equals(name))
				return o;
		return Tab.noObj;
	}
	
	private Obj programObj;
	private Obj currentMethod;
	private Collection<Obj> listOfLocalSymbols;
	private List<DeclaredVariable> listOfVariables = new LinkedList<>();
	
	private int mainPc;
	private int opp;
	
	int getMainPc() {
		return mainPc;
	}

	// =================================================================================================================
	
	public void visit(ProgramClass programClass) {
		Code.dataSize = programObj.getLocalSymbols().size();
	}
	
	public void visit(ProgNameClass progNameClass) {
		String programName = progNameClass.getPName();
		
		programObj = Tab.find(programName);
		listOfLocalSymbols = programObj.getLocalSymbols();
	}

	// =================================================================================================================
	
	@Override
	public void visit(MethodTypeNameVoidClass methodTypeNameVoidClass) {
		String methodName = methodTypeNameVoidClass.getMName();
		
		if ("main".equalsIgnoreCase(methodName)) {
			mainPc = Code.pc;
		}
		
		currentMethod = methodTypeNameVoidClass.obj;
		listOfLocalSymbols = currentMethod.getLocalSymbols();
		
		// Adr field of method.
		methodTypeNameVoidClass.obj.setAdr(Code.pc);
		
		// enter number_params, number_local_variables
		// m(int a, int b) int c; { ... } => enter 2, 3
		Code.put(Code.enter); 
		Code.put(currentMethod.getLevel());  // number of params
		Code.put(listOfLocalSymbols.size()); // number of local variables = number of params + local fields
	}
	
	public void visit(MethodTypeNameOtherClass methodTypeNameOtherClass) {
		currentMethod = methodTypeNameOtherClass.obj;
		listOfLocalSymbols = currentMethod.getLocalSymbols();

		// Adr field of method.
		methodTypeNameOtherClass.obj.setAdr(Code.pc);

		// enter number_params, number_local_variables
		// m(int a, int b) int c; { ... } => enter 2, 3
		Code.put(Code.enter); 
		Code.put(currentMethod.getLevel());  // number of params
		Code.put(listOfLocalSymbols.size()); // number of local variables = number of params + local fields
	}
	
	public void visit(MethodDeclClass methodDeclClass) {
		Struct methodType = currentMethod.getType();
		
		if(methodType.equals(Tab.noType)) {
			Code.put(Code.exit);
			Code.put(Code.return_);
		} else {
			// Code from above is already generated for non-void functions after visiting return statements.
			// So if else is active, that's error.
			Code.put(Code.trap);
	    	Code.put(1);  // Argument of trap call.
		}
		
		currentMethod = null;
		listOfLocalSymbols = programObj.getLocalSymbols();
	}

	// =================================================================================================================
	
	public void visit(VarConstNameClass varConstNameClass) {
		String constName = varConstNameClass.getCName();
		Obj constObj = find(constName);
		CValue cValue = varConstNameClass.getCValue();
		
		if (cValue instanceof CValueNumClass) {
			int constNumValue = ((CValueNumClass)cValue).getN1();
			constObj.setAdr(constNumValue);
		}
		
		if(cValue instanceof CValueCharClass) {
			char constCharValue = ((CValueCharClass)cValue).getC1();
			constObj.setAdr(constCharValue);
		}
		
		if(cValue instanceof CValueBoolClass) {
			boolean constBoolValue = ((CValueBoolClass)cValue).getB1();
			constObj.setAdr(constBoolValue ? 1 : 0);
		}
		
		Code.load(constObj);
	}

	// =================================================================================================================
	
	public void visit(VarEnumDeclClass varEnumDeclClass) {
		String enumName = varEnumDeclClass.getEnumName();
    	Obj enumObj = find(enumName);
    	SymbolDataStructure symbolDataStructure =  enumObj.getType().getMembersTable();
    	
    	for (DeclaredVariable declaredVariable : listOfVariables) {
    		Obj singleEnum = symbolDataStructure.searchKey(declaredVariable.varName);
    		singleEnum.setAdr((Integer)declaredVariable.varValue);
    		
    		Code.load(singleEnum);
    	}
    	
    	listOfVariables.clear();
    }
    
    public void visit(VarEnumValueWithInitClass varEnumValueWithInitClass) {
    	String enumName = varEnumValueWithInitClass.getEName();
    	Integer enumValue = varEnumValueWithInitClass.getN1();
    	
    	DeclaredVariable declaredVariable = new DeclaredVariable(enumName, enumValue);
    	listOfVariables.add(declaredVariable);
    }
    
    public void visit(VarEnumValueWithoutInitClass varEnumValueWithoutInitClass) {
    	String enumName = varEnumValueWithoutInitClass.getEName();
    	int enumValue;
    	
    	if(listOfVariables.isEmpty()) {
    		enumValue = 0;
    	} else {
    		int currentListSize = listOfVariables.size() - 1;
    		DeclaredVariable lastEnumValue = listOfVariables.get(currentListSize);
    		
    		enumValue = (Integer) lastEnumValue.varValue + 1;
    	}
    	
    	DeclaredVariable declaredVariable = new DeclaredVariable(enumName, enumValue);
    	listOfVariables.add(declaredVariable);
    }

    // =================================================================================================================
    
    public void visit(StatementEmptyReturnClass statementEmptyReturnClass) {
    	Code.put(Code.exit);
    	Code.put(Code.return_);
    }
    
    public void visit(StatementReturnValueClass statementReturnValueClass) {
    	Code.put(Code.exit);
    	Code.put(Code.return_);
    }

    // =================================================================================================================
    
    public void visit(StatementReadClass statementReadClass) {
    	Designator designator = statementReadClass.getDesignator();
    	Struct designatorType = designator.obj.getType();

		if (designatorType.equals(Tab.intType) || designatorType.equals(SemanticPass.boolType) || designatorType.getKind() == Struct.Enum) {
			Code.put(Code.read);
		} else {
			Code.put(Code.bread);
		}

    	Code.store(designator.obj);
    }
    
    public void visit(StatementPrintWithNumClass statementPrintWithNumClass) {
    	Expression expression = statementPrintWithNumClass.getExpression();
    	Struct expressionType = expression.struct;
    	
		if (expressionType.equals(Tab.intType) || expressionType.equals(SemanticPass.boolType) || expressionType.getKind() == Struct.Enum) {
			int integerValue = statementPrintWithNumClass.getN2();
			Code.loadConst(integerValue); // Push width on stack.
			Code.put(Code.print);
		}
		else {
			Code.loadConst(1); // Push width on stack.
			Code.put(Code.bprint);
		}
	}
	
	public void visit(StatementPrintWithoutNumClass statementPrintWithoutNumClass) {
		Expression expression = statementPrintWithoutNumClass.getExpression();
    	Struct expressionType = expression.struct;
    	
		if (expressionType.equals(Tab.intType) || expressionType.equals(SemanticPass.boolType) || expressionType.getKind() == Struct.Enum) {
			Code.loadConst(0); // Push width on stack.
			Code.put(Code.print);
		}
		else {
			Code.loadConst(1); // Push width on stack.
			Code.put(Code.bprint);
		}
	}

	//==================================================================================================================
	
	public void visit(StatementDesignatorIncClass statementDesignatorIncClass) {
    	Designator designator = statementDesignatorIncClass.getDesignator();
		
    	Code.load(designator.obj);
    	Code.loadConst(1);
    	Code.put(Code.add);
    	Code.store(designator.obj);
    }
    
    public void visit(StatementDesignatorDecClass statementDesignatorDecClass) {
    	Designator designator = statementDesignatorDecClass.getDesignator();
		
    	Code.load(designator.obj);
    	Code.loadConst(1);
    	Code.put(Code.sub);
    	Code.store(designator.obj);
    }

    // =================================================================================================================
    
    public void visit(DesignatorEnumClass designatorEnumClass) {
    	String enumClassName = designatorEnumClass.getName1();
    	Obj designatorObj = find(enumClassName);
    	SymbolDataStructure symbolDataStructure = designatorObj.getType().getMembersTable();
    	
    	String enumName = designatorEnumClass.getName2();
    	designatorEnumClass.obj = symbolDataStructure.searchKey(enumName);
    }
    
    public void visit(DesignatorArrayClass designatorArrayClass) {
    	String arrayName = designatorArrayClass.getName();
    	Obj obj = find(arrayName);
    	
    	Code.load(obj);
    	Code.put(Code.dup_x1);
    	Code.put(Code.pop);
    }

    //==================================================================================================================
    
    public void visit(ExpressionMinusClass expressionMinusClass) {
    	Code.put(Code.neg);
    }

    // =================================================================================================================
    
    public void visit(TermListClass termListClass) {
    	Code.put(opp);
    }
    
    public void visit(TermFactorAddClass termFactorAddClass) {
    	Code.put(opp);
    }

    // =================================================================================================================
    
    public void visit(AddOpClass add) {
    	opp = Code.add;
    }
    
    public void visit(SubOpClass sub) {
    	opp = Code.sub;
    } 

    //==================================================================================================================
    
    public void visit(MulOpClass mul) {
    	opp = Code.mul;
    }
    
    public void visit(DivOpClass div) {
    	opp = Code.div;
    }
    
    public void visit(ModOpClass mod) {
    	opp = Code.rem;
    }
    
    //==================================================================================================================
    
    public void visit(FactorConstValue factorConstValue) {
		Obj constObj = new Obj(Obj.Con, "", factorConstValue.struct);
		CValue cValue = factorConstValue.getCValue();
		
		if (cValue instanceof CValueNumClass) {
			int constNumValue = ((CValueNumClass)cValue).getN1();
			constObj.setAdr(constNumValue);
		}
		
		if(cValue instanceof CValueCharClass) {
			char constCharValue = ((CValueCharClass)cValue).getC1();
			constObj.setAdr(constCharValue);
		}
		
		if(cValue instanceof CValueBoolClass) {
			boolean constBoolValue = ((CValueBoolClass)cValue).getB1();
			constObj.setAdr(constBoolValue ? 1 : 0);
		}
		
		Code.load(constObj);
	}
    
    public void visit(FactorNewArrayClass factorNewArrayClass) {
    	Code.put(Code.newarray);
        if (factorNewArrayClass.getType().struct == Tab.charType) {
        	Code.put(0); 
        } else {
        	Code.put(1);
        }		
    }
    
    public void visit(FactorFunctionClass factorFunctionClass) {
    	Designator designator = factorFunctionClass.getDesignator();
    	Obj designatorObj = designator.obj;

    	Collection<Obj> collectionOfLocalSymbols = programObj.getLocalSymbols();
    	boolean isParOfLocalSymbols = collectionOfLocalSymbols.contains(designatorObj);
    	
    	if (isParOfLocalSymbols) {
	    	int offset = designatorObj.getAdr() - Code.pc;
	    	Code.put(Code.call);
	    	Code.put2(offset);
    	}
    	else {
    		String functionName = designatorObj.getName();
    		if (functionName.equals("len")) {
    			Code.put(Code.arraylength);
    		}
    	}	
    }
    
    public void visit(FactorDesignerClass factorDesignerClass) {
    	Designator designator = factorDesignerClass.getDesignator();
    	Code.load(designator.obj);
    }

    // =================================================================================================================
    
    public void visit(StatementDesignatorAssignClass statementDesignatorAssignClass) {
    	Designator designator = statementDesignatorAssignClass.getDesignator();
    	Code.store(designator.obj);
    }
    
    public void visit(StatementDesignatorFunctionClass statementDesignatorFunctionClass) {
    	Designator designator = statementDesignatorFunctionClass.getDesignator();
    	Obj designatorObj = designator.obj;
    	
    	Collection<Obj> collectionOfLocalSymbols = programObj.getLocalSymbols();
    	boolean isParOfLocalSymbols = collectionOfLocalSymbols.contains(designatorObj);
    	
    	if (isParOfLocalSymbols) {
	    	int offset = designatorObj.getAdr() - Code.pc;
	    	Code.put(Code.call);
	    	Code.put2(offset);
    	}
    	else {
    		String functionName = designatorObj.getName();
    		if (functionName.equals("len")) {
    			Code.put(Code.arraylength);
    		}
    	}	
    	
    	Struct functionType = designatorObj.getType();
    	if (!functionType.equals(Tab.noType)) {
			Code.put(Code.pop);
		}
    }

    // =================================================================================================================
}
