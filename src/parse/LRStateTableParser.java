package parse;

import debug.ConsoleDebugColor;
import lexer.Lexer;
import lexer.Token;
import symboltable.*;

import java.util.HashMap;
import java.util.Stack;

/**
 * @author dejavudwh isHudw
 */

public class LRStateTableParser {
    public static final String GLOBAL_SCOPE = "global";
    public String symbolScope = GLOBAL_SCOPE;

    private Lexer lexer;
    int lexerInput = 0;
    int nestingLevel = 0;
    String text = "";

    private Stack<Integer> statusStack = new Stack<>();
    private Stack<Object> valueStack = new Stack<>();
    private Object attributeForParentNode = null;
    private Stack<Integer> parseStack = new Stack<>();
    private TypeSystem typeSystem = TypeSystem.getInstance();
    private HashMap<Integer, HashMap<Integer, Integer>> lrStateTable;

    public LRStateTableParser(Lexer lexer) {
        this.lexer = lexer;
        statusStack.push(0);
        valueStack.push(null);
        lexer.advance();
        lexerInput = Token.EXT_DEF_LIST.ordinal();
        lrStateTable = StateNodeManager.getInstance().getLrStateTable();
    }

    public void parse() {
        while (true) {
            Integer action = getAction(statusStack.peek(), lexerInput);

            if (action == null) {
                ConsoleDebugColor.outlnPurple("Shift for input: " + Token.values()[lexerInput].toString());
                System.err.println("The input is denied");
                return;
            }

            if (action > 0) {
                statusStack.push(action);
                text = lexer.text;
                parseStack.push(lexerInput);

                if (Token.isTerminal(lexerInput)) {
                    ConsoleDebugColor.outlnPurple("Shift for input: " + Token.values()[lexerInput].toString() + "   text: " + text);

                    Object obj = takeActionForShift(lexerInput);

                    lexer.advance();
                    lexerInput = lexer.lookAhead;
                    valueStack.push(obj);
                } else {
                    lexerInput = lexer.lookAhead;
                }
            } else {
                if (action == 0) {
                    ConsoleDebugColor.outlnPurple("The input can be accepted");
                    return;
                }

                int reduceProduction = -action;
                Production product = ProductionManager.getInstance().getProductionByIndex(reduceProduction);
                ConsoleDebugColor.outlnPurple("reduce by product: ");
                product.debugPrint();

                takeActionForReduce(reduceProduction);

                int rightSize = product.getRight().size();
                while (rightSize > 0) {
                    parseStack.pop();
                    valueStack.pop();
                    statusStack.pop();
                    rightSize--;
                }

                lexerInput = product.getLeft();
                parseStack.push(lexerInput);
                valueStack.push(attributeForParentNode);
            }
        }
    }

    private Integer getAction(Integer currentState, Integer currentInput) {
        HashMap<Integer, Integer> jump = lrStateTable.get(currentState);
        return jump.get(currentInput);
    }

    private void takeActionForReduce(int productionNum) {
        switch (productionNum) {
            case SyntaxProductionInit.TYPE_TO_TYPE_SPECIFIER:
                attributeForParentNode = typeSystem.newType(text);
                break;

            case SyntaxProductionInit.EnumSpecifier_TO_TypeSpecifier:
                attributeForParentNode = typeSystem.newType("int");
                break;

            case SyntaxProductionInit.StructSpecifier_TO_TypeSpecifier:
                attributeForParentNode = typeSystem.newType(text);
                TypeLink link = (TypeLink) attributeForParentNode;
                Specifier sp = (Specifier) link.getTypeObject();
                sp.setType(Specifier.STRUCTURE);
                StructDefine struct = (StructDefine)valueStack.get(valueStack.size() - 1);
                sp.setStruct(struct);
                break;

            case SyntaxProductionInit.SPECIFIERS_TypeOrClass_TO_SPECIFIERS:
                attributeForParentNode = valueStack.peek();
                Specifier last = (Specifier) ((TypeLink)valueStack.get(valueStack.size() - 2)).getTypeObject();
                Specifier dst = (Specifier) ((TypeLink)attributeForParentNode).getTypeObject();
                typeSystem.specifierCopy(dst, last);
                break;

            case SyntaxProductionInit.NAME_TO_NewName:
            case SyntaxProductionInit.Name_TO_NameNT:
                attributeForParentNode = typeSystem.newSymbol(text, nestingLevel);
                break;

            case SyntaxProductionInit.START_VarDecl_TO_VarDecl:
            case SyntaxProductionInit.Start_VarDecl_TO_VarDecl:
                typeSystem.addDeclarator((Symbol)attributeForParentNode, Declarator.POINTER);
                break;

            case SyntaxProductionInit.VarDecl_LB_ConstExpr_RB_TO_VarDecl:
                Declarator declarator = typeSystem.addDeclarator((Symbol)valueStack.get(valueStack.size() - 4), Declarator.ARRAY);
                int arrayNum = (Integer)attributeForParentNode;
                declarator.setElementNum(arrayNum);
                attributeForParentNode = valueStack.get(valueStack.size() - 4);
                break;

            case SyntaxProductionInit.Name_TO_Unary:
                attributeForParentNode = typeSystem.getSymbolByText(text, nestingLevel, symbolScope);
                break;

            case SyntaxProductionInit.ExtDeclList_COMMA_ExtDecl_TO_ExtDeclList:
            case SyntaxProductionInit.VarList_COMMA_ParamDeclaration_TO_VarList:
            case SyntaxProductionInit.DeclList_Comma_Decl_TO_DeclList:
            case SyntaxProductionInit.DefList_Def_TO_DefList:
                Symbol currentSym = (Symbol)attributeForParentNode;
                Symbol lastSym;
                if (productionNum == SyntaxProductionInit.DefList_Def_TO_DefList) {
                    lastSym = (Symbol)valueStack.get(valueStack.size() - 2);
                } else {
                    lastSym = (Symbol)valueStack.get(valueStack.size() - 3);
                }
                currentSym.setNextSymbol(lastSym);
                break;

            case SyntaxProductionInit.OptSpecifier_ExtDeclList_Semi_TO_ExtDef:
            case SyntaxProductionInit.TypeNT_VarDecl_TO_ParamDeclaration:
            case SyntaxProductionInit.Specifiers_DeclList_Semi_TO_Def:
                Symbol symbol = (Symbol)attributeForParentNode;
                TypeLink specifier = (TypeLink)(valueStack.get(valueStack.size() - 3));
                typeSystem.addSpecifierToDeclaration(specifier, symbol);
                typeSystem.addSymbolsToTable(symbol, symbolScope);

                handleStructVariable(symbol);
                break;

            case SyntaxProductionInit.VarDecl_Equal_Initializer_TO_Decl:
                //如果这里不把attributeForParentNode设置成对应的symbol对象
                //那么上面执行 Symbol symbol = (Symbol)attributeForParentNode; 会出错
                attributeForParentNode = (Symbol) valueStack.get(valueStack.size() - 2);
                break;

            case SyntaxProductionInit.NewName_LP_VarList_RP_TO_FunctDecl:
                setFunctionSymbol(true);
                Symbol argList = (Symbol)valueStack.get(valueStack.size() - 2);
                ((Symbol)attributeForParentNode).args = argList;
                typeSystem.addSymbolsToTable((Symbol)attributeForParentNode, symbolScope);
                symbolScope = ((Symbol)attributeForParentNode).getName();
                Symbol sym = argList;
                while (sym != null) {
                    sym.addScope(symbolScope);
                    sym = sym.getNextSymbol();
                }
                break;

            default:
                break;
        }
    }

    private Object takeActionForShift(int token) {
        if (token == Token.LP.ordinal() || token == Token.LC.ordinal()) {
            nestingLevel++;
        }
        if (token == Token.RP.ordinal() || token == Token.RC.ordinal()) {
            nestingLevel--;
        }

        return null;

    }

    private void handleStructVariable(Symbol symbol) {
        if (symbol == null) {
            return;
        }

        boolean isStruct = false;
        TypeLink typeLink = symbol.typeLinkBegin;
        Specifier specifier = null;
        while (typeLink != null) {
            if (!typeLink.isDeclarator) {
                specifier = (Specifier)typeLink.getTypeObject();
                if (specifier.getType() == Specifier.STRUCTURE) {
                    isStruct = true;
                    break;
                }
            }

            typeLink = typeLink.toNext();
        }

        if (isStruct) {
            StructDefine structDefine = specifier.getStruct();
            Symbol copy = null, headCopy = null, original = structDefine.getFields();
            while (original != null) {
                if (copy != null) {
                    Symbol sym = original.copy();
                    copy.setNextSymbol(sym);
                    copy = sym;
                } else {
                    copy = original.copy();
                    headCopy = copy;
                }

                original = original.getNextSymbol();
            }

            symbol.setArgList(headCopy);
        }
    }

    private void setFunctionSymbol(boolean hasArgs) {
        Symbol funcSymbol;
        if (hasArgs) {
            funcSymbol = (Symbol)valueStack.get(valueStack.size() - 4);
        } else {
            funcSymbol = (Symbol)valueStack.get(valueStack.size() - 3);
        }

        typeSystem.addDeclarator(funcSymbol, Declarator.FUNCTION);
        attributeForParentNode = funcSymbol;
    }
}
