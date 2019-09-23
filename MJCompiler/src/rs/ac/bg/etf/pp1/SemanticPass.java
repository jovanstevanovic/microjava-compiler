package rs.ac.bg.etf.pp1;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Scope;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.structure.SymbolDataStructure;

public class SemanticPass extends VisitorAdaptor {

	private class DeclaredVariable {
		private String varName;
		private Struct varType;
		private boolean isComplexStructure;
		
		DeclaredVariable(String varName, Struct varType) {
			this.varName = varName;
			this.varType = varType;
			isComplexStructure = false;
		}
		
		DeclaredVariable(String varName, Struct varType, boolean isComplexStructure) {
			this.varName = varName;
			this.varType = varType;
			this.isComplexStructure = isComplexStructure;
		}
	}

	private class DeclaredMethod {
		private String methodName;
		private List<Struct> listOfStruts;
		
		DeclaredMethod(String methodName) {
			this.methodName = methodName;
			this.listOfStruts = new LinkedList<>();
		}
	}

	private class DeclaredMethodArgument {
		private String methodName;
		private List<Struct> listOfArguments;

		DeclaredMethodArgument(String methodName) {
			this.methodName = methodName;
			this.listOfArguments = new LinkedList<>();
		}
	}

	private List<Struct> get(String methodName) {
		for(DeclaredMethod declaredMethod : listOfDeclaredMethods) {
			if(declaredMethod.methodName.equals(methodName)) {
				return declaredMethod.listOfStruts;
			}
		}
		
		return new LinkedList<>();
	}

	private List<Struct> getArguments(String methodName) {
		for(DeclaredMethodArgument declaredMethodArgument : listOfMethodArguments) {
			if(declaredMethodArgument.methodName.equals(methodName)) {
				return declaredMethodArgument.listOfArguments;
			}
		}

		return new LinkedList<>();
	}

	private boolean errorDetected = false;
	int printCallCount = 0;
	private Obj currentMethod = null;
	private boolean returnFound = false;
	int nVars;

	private boolean mainMethodExists;
	
	private Logger log = Logger.getLogger(getClass());

	private List<DeclaredVariable> listOfDeclaredVariables = new LinkedList<>();
	private List<DeclaredMethod> listOfDeclaredMethods = new LinkedList<>();
	private List<DeclaredMethodArgument> listOfMethodArguments = new LinkedList<>();

	private List<String> currentMethodsNames = new LinkedList<>();

	static final Struct boolType = Tab.insert(Obj.Type, "bool", new Struct(Struct.Bool)).getType();
	
	private void report_error(String message, SyntaxNode info) {
		errorDetected = true;
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0: info.getLine();
		if (line != 0)
			msg.append (" na liniji ").append(line);
		log.error(msg.toString());
	}

	public void report_info(String message, SyntaxNode info) {
		StringBuilder msg = new StringBuilder(message); 
		int line = (info == null) ? 0: info.getLine();
		if (line != 0)
			msg.append (" na liniji ").append(line);
		log.info(msg.toString());
	}
	// ===================================================================
	
	public void visit(ProgramClass programClass) {
		nVars = Tab.currentScope.getnVars();
		
		Obj progNameObj = programClass.getProgName().obj;
		Tab.chainLocalSymbols(progNameObj);
		Tab.closeScope();
		
		if(!mainMethodExists) {
			report_error("\n**** Semanticka greska: Program nema ulaznu tacku! ****\n", null);
		}
	}
	
	public void visit(ProgNameClass progNameClass) {
		String programName = progNameClass.getPName();
		progNameClass.obj = Tab.insert(Obj.Prog, programName , Tab.noType);
		Tab.openScope();
		
		DeclaredMethod ordDeclaredMethod = new DeclaredMethod("ord");
		listOfDeclaredMethods.add(ordDeclaredMethod);
    	get("ord").add(Tab.charType);
    	
    	DeclaredMethod chrDeclaredMethod = new DeclaredMethod("chr");
    	listOfDeclaredMethods.add(chrDeclaredMethod);
    	get("chr").add(Tab.intType);
    	
    	DeclaredMethod lenDeclaredMethod = new DeclaredMethod("len");
    	listOfDeclaredMethods.add(lenDeclaredMethod);
    	Struct arrayType = new Struct(Struct.Array, Tab.noType);
    	get("len").add(arrayType);
	}

	// ===================================================================
	
	public void visit(VarConstDeclClass varConstDeclClass) {
		Struct constType = varConstDeclClass.getType().struct;
		
		for(DeclaredVariable declaredVariable : listOfDeclaredVariables) {
			if(!constType.equals(declaredVariable.varType)) {
				report_error("\n**** Semanticka greska: Tipovi konstante i dodeljene vrednosti se ne poklapaju! ****\n", varConstDeclClass);
			} else {
				Obj doesObjExists = Tab.find(declaredVariable.varName);
				
				if(doesObjExists == Tab.noObj) { // Is already declared in current const list?
					Tab.insert(Obj.Con, declaredVariable.varName, declaredVariable.varType);
				} else {
					report_error("\n**** Semanticka greska: Konstanta " + declaredVariable.varName + " je vec deklarisana! ****\n", varConstDeclClass);
				}
				
			}
		}
		
		listOfDeclaredVariables.clear();
	}
	
	public void visit(VarConstNameClass varConstNameClass) {
		String constantName = varConstNameClass.getCName();
		Obj doesExists = Tab.find(constantName);
		
		if(doesExists == Tab.noObj) { // Is already declared some of previous const lists?
			Struct declaredVariableType = varConstNameClass.getCValue().struct;
			DeclaredVariable declaredVariable = new DeclaredVariable(constantName, declaredVariableType);
			listOfDeclaredVariables.add(declaredVariable);
		} else {
			report_error("\n**** Semanticka greska: Konstanta " + constantName + " je vec deklarisana! ****\n", varConstNameClass);
		}
	}

	// ===================================================================
	
	public void visit(VarEnumDeclClass varEnumDeclClass) {
		String enumCollectionName = varEnumDeclClass.getEnumName();
		Obj enumCollectionObj = Tab.find(enumCollectionName);
		
		if(enumCollectionObj.equals(Tab.noObj)) {
			Struct enumCollectionType = new Struct(Struct.Enum);
			Tab.insert(Obj.Type, enumCollectionName, enumCollectionType);
			SymbolDataStructure symbolDataStructure = enumCollectionType.getMembersTable();
			
	    	for (DeclaredVariable declaredVariable : listOfDeclaredVariables) {	
	    		String declaredVariableName = declaredVariable.varName;
	    		
	    		if (symbolDataStructure.searchKey(declaredVariableName) == null) {
	    			Obj enumObj = new Obj(Obj.Con, declaredVariableName, Tab.intType);
	    			symbolDataStructure.insertKey(enumObj);
	    		}
	    		else {
	    			report_error("\n**** Semanticka greska: Enum polje " + declaredVariable.varName + " je vec definisano u kolekciji " + enumCollectionName + "! ****\n", varEnumDeclClass);
	    		}	
	    	}
		} else {
			report_error("\n**** Semanticka greska: Enum " + enumCollectionName + " je vec deklarisan! ****\n", varEnumDeclClass);
		}
		
		listOfDeclaredVariables.clear();
	}
	
	public void visit(VarEnumValueWithInitClass valueWithInitClass) {
		String enumName = valueWithInitClass.getEName();
		DeclaredVariable declaredVariable = new DeclaredVariable(enumName, null);
		listOfDeclaredVariables.add(declaredVariable);
	}
	
	public void visit(VarEnumValueWithoutInitClass valueWithoutInitClass) {
		String enumName = valueWithoutInitClass.getEName();
		DeclaredVariable declaredVariable = new DeclaredVariable(enumName, null);
		listOfDeclaredVariables.add(declaredVariable);
	}

	// ===================================================================
	
	public void visit(VarDeclVarSingleClass varDeclVarSingleClass) {
		Struct varType = varDeclVarSingleClass.getType().struct;
		Scope currentScope = Tab.currentScope();
		
		for(DeclaredVariable declaredVariable : listOfDeclaredVariables) {
			String declaredVariableName = declaredVariable.varName;
			Obj varObj = currentScope.findSymbol(declaredVariableName);
			
			if(varObj != null) {
				report_error("\n**** Semanticka greska: Promenljiva " + declaredVariableName + " je vec deklarisana! ****\n", varDeclVarSingleClass);
			} else {
				if(declaredVariable.isComplexStructure) {
					Struct declaredVariableType = new Struct(Struct.Array, varType);
					Tab.insert(Obj.Var, declaredVariableName, declaredVariableType);
				} else {
					Tab.insert(Obj.Var, declaredVariableName, varType);
				}
			}
		}
		
		listOfDeclaredVariables.clear();
	}

	public void visit(VarDeclSingleArrayClass varDeclSingleArrayClass) {
		String declaredVariableName = varDeclSingleArrayClass.getArray();
		Scope currentScope = Tab.currentScope();
		Obj varObj = currentScope.findSymbol(declaredVariableName);
		
		if(varObj == null) {
			DeclaredVariable declaredVariable = new DeclaredVariable(declaredVariableName, null, true);
			listOfDeclaredVariables.add(declaredVariable);
		} else {
			report_error("\n**** Semanticka greska: Promenljiva " + declaredVariableName + " je vec deklarisana! ****\n", varDeclSingleArrayClass);
		}
	}
	
	public void visit(VarDeclSingleSingleClass varDeclSingleSingleClass) {
		String declaredVariableName = varDeclSingleSingleClass.getVName();
		Scope currentScope = Tab.currentScope();
		Obj varObj = currentScope.findSymbol(declaredVariableName);
		
		if(varObj == null) {
			DeclaredVariable declaredVariable = new DeclaredVariable(declaredVariableName, null);
			listOfDeclaredVariables.add(declaredVariable);
		} else {
			report_error("\n**** Semanticka greska: Promenljiva " + declaredVariableName + " je vec deklarisana! ****\n", varDeclSingleSingleClass);
		}
	}

	// ===================================================================
	
	public void visit(MethodDeclClass methodDeclClass) {
		Tab.chainLocalSymbols(currentMethod);
    	Tab.closeScope();

    	Struct methodReturnType = currentMethod.getType();
    	String methodName = currentMethod.getName();

    	if (!returnFound && methodReturnType != Tab.noType) {
    		report_error("\n**** Semanticka greska: Metoda " + methodName + " nema return iskaz! ****\n", methodDeclClass);
    	}
		
    	if (methodName.equals("main") && !get("main").isEmpty()) {
    		report_error("\n**** Semanticka greska: Main funkcija ne sme imati parametre! ****\n", methodDeclClass);
    	}
		
    	returnFound = false;
    	currentMethod = null;
	}
	
	public void visit(MethodTypeNameVoidClass methodTypeNameVoidClass) {
		String methodName = methodTypeNameVoidClass.getMName();

		Obj isMethodExists = Tab.find(methodName);
		if(!isMethodExists.equals(Tab.noObj)) {
			report_error("\n**** Semanticka greska: Redefinicija " + methodName + " funkcije! ****\n", methodTypeNameVoidClass);
		}

		methodTypeNameVoidClass.obj = Tab.insert(Obj.Meth, methodName, Tab.noType);
		
		currentMethod = methodTypeNameVoidClass.obj;

    	Tab.openScope();
    	
    	DeclaredMethod declaredMethod = new DeclaredMethod(methodName);
    	listOfDeclaredMethods.add(declaredMethod);
    	
    	if (methodName.equals("main")) {
    		if (mainMethodExists) {
    			report_error("\n**** Semanticka greska: Redefinicija main funkcije! ****\n", methodTypeNameVoidClass);
    		}
    		else {
    			mainMethodExists = true;
    		}	
    	}
	}
	
	public void visit(MethodTypeNameOtherClass methodTypeNameOtherClass) {
		String methodName = methodTypeNameOtherClass.getMName();
		Struct methodReturnType = methodTypeNameOtherClass.getType().struct;

		Obj isMethodExists = Tab.find(methodName);
		if(!isMethodExists.equals(Tab.noObj)) {
			report_error("\n**** Semanticka greska: Redefinicija " + methodName + " funkcije! ****\n", methodTypeNameOtherClass);
		}

		methodTypeNameOtherClass.obj = Tab.insert(Obj.Meth, methodName, methodReturnType);
		currentMethod = methodTypeNameOtherClass.obj;
    	Tab.openScope();

    	DeclaredMethod declaredMethod = new DeclaredMethod(methodName);
    	listOfDeclaredMethods.add(declaredMethod);
    	
    	if (methodName.equals("main")) {
    		if (mainMethodExists) {
    			report_error("\n**** Semanticka greska: Redefinicija main funkcije! ****\n", methodTypeNameOtherClass);
    		}
    		else {
    			mainMethodExists = true;
    		}
    		
    		report_error("\n**** Semanticka greska: Main mora imati povratni tip VOID! ****\n", methodTypeNameOtherClass);
    	}
	}

	// ===================================================================
	
	public void visit(FormParamDeclVarClass formParamDeclVarClass) {
		String formParamName = formParamDeclVarClass.getName();
		Struct formParamType = formParamDeclVarClass.getType().struct;
		
		Scope currentScope = Tab.currentScope();
		int currentMethodLevel = currentMethod.getLevel(); // Level == number of arguments
		String currentMethodName = currentMethod.getName();

		if (currentScope.findSymbol(formParamName) == null) {
    		Obj obj = Tab.insert(Obj.Var, formParamName, formParamType);
    		obj.setFpPos(currentMethodLevel);
    		currentMethod.setLevel(currentMethodLevel + 1);
    		
    		Struct objType = obj.getType();
    		get(currentMethodName).add(objType);
    	}
    	else {
    		report_error("\n**** Semanticka greska: Formalni parametar " + formParamName + " je vec definisan u funkciji! ****\n" + currentMethodName, formParamDeclVarClass);
    	}
	}
	
	public void visit(FormParamDeclArrayClass formParamDeclArrayClass) {
		String formParamName = formParamDeclArrayClass.getName();
		Struct formParamType = formParamDeclArrayClass.getType().struct;

		Scope currentScope = Tab.currentScope();
		int currentMethodLevel = currentMethod.getLevel(); // Level == number of arguments
		String currentMethodName = currentMethod.getName();
		
		if (currentScope.findSymbol(formParamName) == null) {
			Struct formParamArrayType = new Struct(Struct.Array, formParamType);
    		Obj obj = Tab.insert(Obj.Var, formParamName, formParamArrayType);
    		obj.setFpPos(currentMethodLevel);
    		currentMethod.setLevel(currentMethodLevel + 1);
    		
    		Struct objType = obj.getType();
    		get(currentMethodName).add(objType);
    	}
    	else {
    		report_error("\n**** Semanticka greska: Formalni parametar " + formParamName + " je vec definisan u funkciji! ****\n" + currentMethodName, formParamDeclArrayClass);
    	}
    		
	}

	// ====================================================================
	
	public void visit(TypeClass typeClass) {
		String typeName = typeClass.getTypeName();
    	Obj typeObj = Tab.find(typeName);
    	
    	if (typeObj.equals(Tab.noObj)) {
    		report_error("\n**** Semanticka greska: Tip " + typeName + " ne postoji! ****\n", typeClass);
    		typeClass.struct = Tab.noType;
    	}
    	else {
    		if (typeObj.getKind() == Obj.Type) {
    			typeClass.struct = typeObj.getType();
    		} else {
        		report_error("\n**** Semanticka greska: Tip " + typeName + " ne prestavlja tip podatka! ****\n", typeClass);
        		typeClass.struct = Tab.noType;
        	}
    	}
    }
    
    //=====================================================================
    
    public void visit(CValueNumClass cValueNumClass) {
    	cValueNumClass.struct = Tab.intType;
    }
    
    public void visit(CValueCharClass cValueCharClass) {
    	cValueCharClass.struct = Tab.charType;
    }
    
    public void visit(CValueBoolClass cValueBoolClass) {
    	cValueBoolClass.struct = boolType;
    }

    // =====================================================================
    
    public void visit(StatementEmptyReturnClass statementEmptyReturnClass) {
    	returnFound = true;
    	
    	Struct currentMethodReturnType = currentMethod.getType();
    	String currentMethodName = currentMethod.getName();
    	
    	if (!currentMethodReturnType.equals(Tab.noType)) {
    		report_error("\n**** Semanticka greska: Funkcija " + currentMethodName + " ne vraca vrednost! ****\n", statementEmptyReturnClass);
    	}
    }
    
    public void visit(StatementReturnValueClass statementReturnValueClass) {
    	returnFound = true;
    	
    	String currentMethodName = currentMethod.getName();
    	Struct returnExpressionType = statementReturnValueClass.getExpression().struct;
    	Struct currentMethodType = currentMethod.getType();

    	boolean isReturnTypeAdequate = returnExpressionType.equals(currentMethodType) || (returnExpressionType.getKind() == Struct.Enum && currentMethodType.getKind() == Struct.Int)
				|| (currentMethodType.getKind() == Struct.Enum && returnExpressionType.getKind() == Struct.Int);
    	if (!isReturnTypeAdequate) {
    		report_error("\n**** Semanticka greska: Funkcija " + currentMethodName + " ne vraca kompatibilan tip vrednost! ****\n", statementReturnValueClass);
    	}		
    }

    // =====================================================================
	
    public void visit(StatementReadClass statementReadClass) {
    	Obj designatorObj = statementReadClass.getDesignator().obj;
    	Struct designatorType = designatorObj.getType();
    	int objKind = designatorObj.getKind();

    	boolean isAdequateType = designatorType.equals(Tab.intType) || designatorType.equals(Tab.charType) || designatorType.equals(boolType);
    	if (objKind == Obj.Var || objKind == Obj.Elem) {
    		if (!isAdequateType) {
    			report_error("\n**** Semanticka greska: Read funkcija prihavata samo primitivne tipove! ****\n", statementReadClass);
    		}
    	}
    	else {
    		report_error("\n**** Semanticka greska: Read funkcija ne prima argumente koji nisu varijable! ****\n", statementReadClass);
    	}	
    }
    
    public void visit(StatementPrintWithNumClass statementPrintWithNumClass) {
    	Struct designatorType = statementPrintWithNumClass.getExpression().struct;

    	boolean isAdequateType = designatorType.equals(Tab.intType) || designatorType.equals(Tab.charType)
    			|| designatorType.equals(boolType) || Struct.Enum == designatorType.getKind();
		if (!isAdequateType) {
			report_error("\n**** Semanticka greska: Print funkcija prihavata samo primitivne tipove! ****\n", statementPrintWithNumClass);
		}
    }
    
    public void visit(StatementPrintWithoutNumClass statementPrintWithoutNumClass) {
    	Struct designatorType = statementPrintWithoutNumClass.getExpression().struct;

    	boolean isAdequateType = designatorType.equals(Tab.intType) || designatorType.equals(Tab.charType)
    			|| designatorType.equals(boolType) || Struct.Enum == designatorType.getKind();
		if (!isAdequateType) {
			report_error("\n**** Semanticka greska: Print funkcija prihavata samo primitivne tipove! ****\n", statementPrintWithoutNumClass);
		}
    }

    // =====================================================================
	
    public void visit(ActualParamrListClass actualParamrListClass) {
    	Struct actualParamExpressionType = actualParamrListClass.getExpression().struct;

    	String currentMethodName = currentMethodsNames.get(currentMethodsNames.size() - 1);
    	getArguments(currentMethodName).add(actualParamExpressionType);
    }
    
    public void visit(ActualParamListSingleClass actualParamListSingleClass) {
    	Struct actualParamExpressionType = actualParamListSingleClass.getExpression().struct;

		String currentMethodName = currentMethodsNames.get(currentMethodsNames.size() - 1);
		getArguments(currentMethodName).add(actualParamExpressionType);
    }

    // =====================================================================
    
    public void visit(ExpressionMinusClass expressionMinusClass) {
    	TermList listOfTerminals = expressionMinusClass.getTermList();
    	expressionMinusClass.struct = listOfTerminals.struct;

    	Struct listOfTerminalsType = listOfTerminals.struct;
    	boolean isExpressionAdequate = listOfTerminalsType.equals(Tab.intType) || listOfTerminalsType.getKind() == Struct.Enum;
    	
    	if (!isExpressionAdequate) {
    		report_error("\n**** Semanticka greska: Necelobrojni tip ima negativan predznak! ****\n", expressionMinusClass);
    	}
    }
    
    public void visit(ExpressionPlusClass expressionPlusClass) {
    	TermList listOfTerminals = expressionPlusClass.getTermList();
    	expressionPlusClass.struct = listOfTerminals.struct;
    }

    // =====================================================================
    
    public void visit(TermListClass termListClass) {
    	Term term = termListClass.getTerm();
    	Struct termType = term.struct;
    	termListClass.struct = termType;
    	
    	TermList termList = termListClass.getTermList();
    	Struct termListType = termList.struct;
    	
    	boolean isTermListTypeInaccurate = !termListType.equals(Tab.intType) && !(termListType.getKind() == Struct.Enum);
    	boolean isTermTypeInaccurate = !termType.equals(Tab.intType) && !(termType.getKind() == Struct.Enum);
    	if (isTermListTypeInaccurate || isTermTypeInaccurate) {
			report_error("\n**** Semanticka greska: Operandi sabiranja/oduzimanja moraju biti celobrojnog tipa! ****\n", termListClass);
		}
    }
    
    public void visit(TermListSingleClass termListSingleClass) {
    	termListSingleClass.struct = termListSingleClass.getTerm().struct;
    }

    // =====================================================================
    
    public void visit(TermFactorAddClass termFactorAddClass) {
    	Factor factor = termFactorAddClass.getFactor();
    	Struct factorStruct = factor.struct;
		int factorKind = factorStruct.getKind();

    	Term term = termFactorAddClass.getTerm();
    	Struct termStruct = term.struct;
    	int termKind = termStruct.getKind();
    	
    	termFactorAddClass.struct = factor.struct;

    	boolean isAdequateTypeTerm = termStruct.equals(Tab.intType) || termKind == Struct.Enum;
    	boolean isAdequateTypeFactor = factorStruct.equals(Tab.intType) || factorKind == Struct.Enum;
    	if (!isAdequateTypeTerm || !isAdequateTypeFactor) {
    		report_error("\n**** Semanticka greska: Operandi nisu celobrojnog ili ENUM tipa! ****\n", termFactorAddClass);
    	}	
    }
    
    public void visit(TermFactorSingleClass termFactorSingleClass) {
    	Factor factorClass = termFactorSingleClass.getFactor();
    	termFactorSingleClass.struct = factorClass.struct;
    }
    
    public void visit(FactorConstValue factorConstValue) {
    	CValue constantValue = factorConstValue.getCValue();
    	factorConstValue.struct = constantValue.struct;
    }
    
    public void visit(FactorNewArrayClass factorNewArrayClass) {
    	Expression expression = factorNewArrayClass.getExpression();
    	Struct expressionType = expression.struct;
    	Struct arrayType = factorNewArrayClass.getType().struct;
    	
    	boolean isArrayLengthTypeIsInaccurate = !expressionType.equals(Tab.intType) && !(expressionType.getKind() == Struct.Enum);
    	if (isArrayLengthTypeIsInaccurate) {
    		report_error("\n**** Semanticka greska: Duzina niza nije celobrojnog tipa! ****\n", factorNewArrayClass);
			factorNewArrayClass.struct = new Struct(Struct.Array, Tab.noType);
    	} else {
    		factorNewArrayClass.struct = new Struct(Struct.Array, arrayType);
    	}
    }
    
    public void visit(FactorNewObjectClass factorNewObjectClass) {
    	Type objType = factorNewObjectClass.getType();
    	factorNewObjectClass.struct = objType.struct;
    }
    
    public void visit(FactorExpressionClass factorExpressionClass) {
    	Expression expression = factorExpressionClass.getExpression();
    	factorExpressionClass.struct = expression.struct;
    }
    
    public void visit(FactorDesignerClass factorDesignerClass) {
    	Designator designator = factorDesignerClass.getDesignator();
    	factorDesignerClass.struct = designator.obj.getType();
    }

    // =======================================================================
    
    public void visit(StatementDesignatorIncClass statementDesignatorIncClass) {
    	Obj designatorObj = statementDesignatorIncClass.getDesignator().obj;
    	Struct designatorType = designatorObj.getType();
    	int designatorKind = designatorObj.getKind();
    	
    	boolean isDesignatorTypeAdequate = designatorType.equals(Tab.intType);
    	boolean isDesignatorKindAdequate = designatorKind == Obj.Elem || designatorKind == Obj.Var;
    	if(!isDesignatorTypeAdequate) {
    		report_error("\n**** Semanticka greska: Operand nije celobrojnog tipa! ****\n", statementDesignatorIncClass);
    	} else {
    		if(!isDesignatorKindAdequate) {
    			report_error("\n**** Semanticka greska: Operand nije lvrednost! ****\n", statementDesignatorIncClass);
    		}
    	}
    }
    
    public void visit(StatementDesignatorDecClass statementDesignatorDecClass) {
    	Obj designatorObj = statementDesignatorDecClass.getDesignator().obj;
    	Struct designatorType = designatorObj.getType();
    	int designatorKind = designatorObj.getKind();
    	
    	boolean isDesignatorTypeAdequate = designatorType.equals(Tab.intType);
    	boolean isDesignatorKindAdequate = designatorKind == Obj.Elem || designatorKind == Obj.Var;
    	if(!isDesignatorTypeAdequate) {
    		report_error("\n**** Semanticka greska: Operand nije celobrojnog tipa! ****\n", statementDesignatorDecClass);
    	} else {
    		if(!isDesignatorKindAdequate) {
    			report_error("\n**** Semanticka greska: Operand nije lvrednost! ****\n", statementDesignatorDecClass);
    		}
    	}
    }
    
    public void visit(DesignatorEnumClass designatorEnumClass) {
    	String enumName = designatorEnumClass.getName1();
    	String accessedEnumName = designatorEnumClass.getName2();
    	Obj designatorObj = Tab.find(enumName);
    	
    	if (designatorObj.equals(Tab.noObj)) {
    		report_error("\n**** Semanticka greska: Enum klasa " + enumName + " nije deklarisan! ****\n", designatorEnumClass);
    	} else {
    		SymbolDataStructure listOfEnums = designatorObj.getType().getMembersTable();
    		
    		if (listOfEnums.searchKey(accessedEnumName) == null) {
    			report_error("\n**** Semanticka greska: Enum " + accessedEnumName + " nije deklarisan! ****\n", designatorEnumClass);
    		}
    	}
    	
    	designatorEnumClass.obj = designatorObj;
    }
    
    public void visit(DesignatorArrayClass designatorArrayClass) {
    	String arrayName = designatorArrayClass.getName();
    	Obj designatorObj = Tab.find(arrayName);
    	Struct arrayType = designatorObj.getType().getElemType();
    	Expression expression = designatorArrayClass.getExpression();
    	Struct expressionType = expression.struct;
    	
    	if (designatorObj.equals(Tab.noObj)) {
    		report_error("\n**** Semanticka greska: Niz " + arrayName + " nije deklarisan! ****\n", designatorArrayClass);
    	} else {
        	int designatorKind = designatorObj.getType().getKind();
    		
        	if (designatorKind != Struct.Array) {
    			report_error("\n**** Semanticka greska: Promenjiva " + arrayName + " nije niz! ****\n", designatorArrayClass);
    		} else {
				boolean isExpressionTypeAdequate = expressionType.equals(Tab.intType) || expressionType.getKind() == Struct.Enum;

				if (!isExpressionTypeAdequate) {
					report_error("\n**** Semanticka greska: Indeks nije celobrojnog tipa! ****\n", designatorArrayClass);
				}
			}
    	}
    	
    	if(arrayType != null) {
    		designatorArrayClass.obj = new Obj(Obj.Elem, arrayName, arrayType);
    	} else {
    		designatorArrayClass.obj = new Obj(Obj.Elem, arrayName, Tab.noType);
    	}	
    }
    
    public void visit(DesignatorVarClass designatorVarClass) {
    	String designatorName = designatorVarClass.getName();
    	Obj designatorObj = Tab.find(designatorName);
    	
    	if (designatorObj.equals(Tab.noObj)) {
    		report_error("\n**** Semanticka greska: Promenjiva " + designatorName + " nije deklarisan! ****\n", designatorVarClass);
    	}

    	if(designatorObj.getKind() == Obj.Meth) {
    		currentMethodsNames.add(designatorName);
			DeclaredMethodArgument declaredMethodArgument = new DeclaredMethodArgument(designatorName);
			listOfMethodArguments.add(declaredMethodArgument);
		}

    	designatorVarClass.obj = designatorObj;
    }
    
    public void visit(StatementDesignatorAssignClass statementDesignatorAssignClass) {
    	Obj designatorObj = statementDesignatorAssignClass.getDesignator().obj;
    	Expression expressionObj = statementDesignatorAssignClass.getExpression();
    			
    	Struct designatorObjType = designatorObj.getType();
    	int designatorKind = designatorObj.getKind();
    	Struct expressionObjType = expressionObj.struct;
    	
    	boolean isDesignatorTypeAdequate = designatorKind == Obj.Var || designatorKind == Obj.Elem;
    	boolean isDesignatorAndExpressionCompatible = designatorObjType.equals(expressionObjType)
    			|| (designatorObjType.equals(Tab.intType) && expressionObjType.getKind() == Struct.Enum);
    			
    	if(!isDesignatorTypeAdequate) {
    		report_error("\n**** Semanticka greska: Izraz na levoj strani dodele nije lvrednost! ****\n", statementDesignatorAssignClass);
    	} else {
    		if(!isDesignatorAndExpressionCompatible) {
    			report_error("\n**** Semanticka greska: Izraz na levoj i desnoj strani nisu kompatibilni! ****\n", statementDesignatorAssignClass);
    		}
    	}	
    }

    // ========================================================================
    
    public void visit(FactorFunctionClass factorFunctionClass) {
    	Designator designator = factorFunctionClass.getDesignator();
    	Obj designatorObj = designator.obj;
    	String functionName = designatorObj.getName();
    	
    	boolean isDesignatorMethod = designatorObj.getKind() == Obj.Meth;
    	if (!isDesignatorMethod) {
    		report_error("\n**** Semanticka greska: Promenjiva " + functionName + " nije funkcija! ****\n", factorFunctionClass);
    	} else {
    		int argumentListSize = getArguments(functionName).size();
    		int requestedArgumentSize = get(functionName).size();

    		boolean isSizesEqual = argumentListSize == requestedArgumentSize;
    		if (!isSizesEqual) {
    			report_error("\n**** Semanticka greska: Funkcija " + functionName + " nema isti broj argumenata! ****\n", factorFunctionClass);
    		} else {
    			int numberOfArguments = getArguments(functionName).size();
    			List<Struct> listOfArgumentsType = get(functionName);
    			
	    		for (int i = 0; i < numberOfArguments; i++) {
	    			Struct parameterType = listOfArgumentsType.get(i);
	    			Struct argumentType = getArguments(functionName).get(i);

	    			boolean isTypesCompatible = parameterType.equals(argumentType) || (parameterType.equals(Tab.intType) && argumentType.getKind() == Struct.Enum)
							|| (parameterType.getKind() == Struct.Array && argumentType.getKind() == Struct.Array && parameterType.getElemType() == Tab.noType);
	    			if (!isTypesCompatible) {
	    				report_error("\n**** Semanticka greska: Argumenti i parametri se ne poklapaju u funkciji " + functionName + "! ****\n", factorFunctionClass);
	    				break;
	    			}
	    		}
    		}
    	}
		
    	factorFunctionClass.struct = designatorObj.getType();

		getArguments(functionName).clear();
		listOfMethodArguments.remove(listOfMethodArguments.size() - 1);

		currentMethodsNames.remove(currentMethodsNames.size() - 1);
    }
    
    public void visit(StatementDesignatorFunctionClass statementDesignatorFunctionClass) {
    	Designator designator = statementDesignatorFunctionClass.getDesignator();
    	Obj designatorObj = designator.obj;
    	String functionName = designatorObj.getName();
    	
    	boolean isDesignatorMethod = designatorObj.getKind() == Obj.Meth;
    	if (!isDesignatorMethod) {
    		report_error("\n**** Semanticka greska: Promenjiva " + functionName + " nije funkcija! ****\n", statementDesignatorFunctionClass);
    	} else {
    		int argumentListSize = getArguments(functionName).size();
    		int requestedArgumentSize = get(functionName).size();

    		boolean isSizesEqual = argumentListSize == requestedArgumentSize;
    		if (!isSizesEqual) {
    			report_error("\n**** Semanticka greska: Funkcija " + functionName + " nema isti broj argumenata! ****\n", statementDesignatorFunctionClass);
    		} else {
    			int numberOfArguments = getArguments(functionName).size();
    			List<Struct> listOfArgumentsType = get(functionName);
    			
	    		for (int i = 0; i < numberOfArguments; i++) {
					Struct parameterType = listOfArgumentsType.get(i);
					Struct argumentType = getArguments(functionName).get(i);

					boolean isTypesCompatible = parameterType.equals(argumentType) || (parameterType.equals(Tab.intType) && argumentType.getKind() == Struct.Enum)
							|| (parameterType.getKind() == Struct.Array && argumentType.getKind() == Struct.Array && parameterType.getElemType() == Tab.noType);
	    			if (!isTypesCompatible) {
	    				report_error("\n**** Semanticka greska: Argumenti i parametri se ne poklapaju u funkciji " + functionName + "! ****\n", statementDesignatorFunctionClass);
	    				break;
	    			}
	    		}	
    		}
    	}

		getArguments(functionName).clear();
		listOfMethodArguments.remove(listOfMethodArguments.size() - 1);

		currentMethodsNames.remove(currentMethodsNames.size() - 1);
    }

    // ==========================================================================================================

	boolean passed() {
		return !errorDetected;
	}
}

