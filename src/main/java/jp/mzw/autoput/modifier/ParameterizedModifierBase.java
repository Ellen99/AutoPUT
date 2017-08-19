package jp.mzw.autoput.modifier;

import jp.mzw.autoput.ast.ASTUtils;
import jp.mzw.autoput.core.Project;
import jp.mzw.autoput.core.TestCase;
import jp.mzw.autoput.core.TestSuite;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by TK on 7/28/17.
 */
public class ParameterizedModifierBase extends AbstractModifier {
    // ユーザが変更可能
    protected static final String CLASS_NAME = "AutoPUT";
    protected static final String METHOD_NAME = "autoPutTest";
    protected static final String INPUT_VAR = "input";
    protected static final String EXPECTED_VAR = "expected";

    // 変更不可
    protected static final String DATA_METHOD = "data";

    protected AST ast;
    protected CompilationUnit cu;
    protected ASTRewrite rewrite;


    public ParameterizedModifierBase(Project project) {
        super(project);
    }

    public ParameterizedModifierBase(TestSuite testSuite) {
        super(testSuite);
    }

    public CompilationUnit getCompilationUnit() {
        return testSuite.getCu();
    }

    @Override
    public void modify(MethodDeclaration method) {
        cu = getCompilationUnit();
        ast = cu.getAST();
        System.out.println(method);
        System.out.println("-------------------");
        rewrite = ASTRewrite.create(ast);

        // テストメソッドを作成(既存のテストメソッドを修正する)
        modifyTestMethod(method);
        // dataメソッドを作成
        createDataMethod(method);
        // 既存のテストメソッドとコンストラクタを全て削除
        deleteExistingMethodDeclarations(method);
        // 既存のフィールド変数を削除
        deleteExistingFieldDeclarations();
        // import文を追加
        addImportDeclarations();
        // フィールド変数を追加
        addFieldDeclarations(method);
        // コンストラクタを追加
        addConstructor(method);
        // クラス名を変更，修飾子も追加
        modifyClassInfo();

        // modify
        try {
            Document document = new Document(testSuite.getTestSources());
            TextEdit edit = rewrite.rewriteAST(document, null);
            edit.apply(document);
            System.out.println("=========================");
            System.out.println(document.get());
            System.out.println("=========================");
        } catch (IOException | BadLocationException e) {
            e.printStackTrace();
        }
        // initialize
        this.cu = null;
        this.ast = null;
        this.rewrite = null;
        return;
    }

    protected void addImportDeclarations() {
        ListRewrite listRewrite = rewrite.getListRewrite(getCompilationUnit(), CompilationUnit.IMPORTS_PROPERTY);
        // import文を生成
        ImportDeclaration runWith = ast.newImportDeclaration();
        runWith.setName(ast.newName(new String[]{"org", "junit", "runner", "RunWith"}));
        ImportDeclaration parameterized = ast.newImportDeclaration();
        parameterized.setName(ast.newName(new String[] {"org", "junit", "runners", "Parameterized"}));
        ImportDeclaration parameters = ast.newImportDeclaration();
        parameters.setName(ast.newName(new String[] {"org", "junit", "runners", "Parameters"}));
        // importを付与
        listRewrite.insertLast(runWith, null);
        listRewrite.insertLast(parameterized, null);
        listRewrite.insertLast(parameters, null);
    }


    protected void modifyClassInfo() {
        TypeDeclaration modified = getTargetType();
        // 修飾子を変更
        ListRewrite modifiersListRewrite = rewrite.getListRewrite(modified, TypeDeclaration.MODIFIERS2_PROPERTY);
        // RunWithアノテーションを付与
        SingleMemberAnnotation annotation = ASTUtils.getRunWithAnnotation(ast);
        modifiersListRewrite.insertLast(annotation, null);
        // クラス名を変更
        modified.setName(ast.newSimpleName(CLASS_NAME));


    }

    protected void addFieldDeclarations(MethodDeclaration origin) {
        TypeDeclaration modified = getTargetType();
        // フィールド変数定義を生成
        FieldDeclaration inputDeclaration = createFieldDeclaration(getInputType(origin), INPUT_VAR);
        FieldDeclaration expectedDeclaration = createFieldDeclaration(getExpectedType(origin), EXPECTED_VAR);
        // フィールド変数定義を追加
        ListRewrite bodyDeclarationsListRewrite = rewrite.getListRewrite(modified, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        bodyDeclarationsListRewrite.insertLast(inputDeclaration, null);
        bodyDeclarationsListRewrite.insertLast(expectedDeclaration, null);
    }

    protected FieldDeclaration createFieldDeclaration(Type type, String var) {
        VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
        fragment.setName(ast.newSimpleName(var));
        FieldDeclaration fieldDeclaration = ast.newFieldDeclaration(fragment);
        fieldDeclaration.setType(type);
        fieldDeclaration.modifiers().add(ASTUtils.getPrivateModifier(ast));
        return fieldDeclaration;
    }


    protected void addConstructor(MethodDeclaration origin) {
        // コンストラクタを生成
        MethodDeclaration constructor = ast.newMethodDeclaration();
        constructor.setConstructor(true);
        // 名前を設定
        constructor.setName(ast.newSimpleName(CLASS_NAME));
        ListRewrite modifiersListRewrite = rewrite.getListRewrite(constructor, MethodDeclaration.MODIFIERS2_PROPERTY);
        // public修飾子を付与
        modifiersListRewrite.insertLast(ASTUtils.getPublicModifier(ast), null);
        // 引数を設定
        // inputの型と名前を設定
        SingleVariableDeclaration input = ast.newSingleVariableDeclaration();
        input.setType(getInputType(origin));
        input.setName(ast.newSimpleName(INPUT_VAR));
        // expectedの型と名前を設定
        SingleVariableDeclaration expected = ast.newSingleVariableDeclaration();
        expected.setType(getExpectedType(origin));
        expected.setName(ast.newSimpleName(EXPECTED_VAR));
        // 引数を追加
        ListRewrite parametersListRewrite = rewrite.getListRewrite(constructor, MethodDeclaration.PARAMETERS_PROPERTY);
        parametersListRewrite.insertLast(input, null);
        parametersListRewrite.insertLast(expected, null);
        // bodyを設定
        Block body = ast.newBlock();
        ExpressionStatement inputStatement = createConstructorBlockAssignment(INPUT_VAR);
        ExpressionStatement expectedStatement = createConstructorBlockAssignment(EXPECTED_VAR);
        ListRewrite bodyListRewrite = rewrite.getListRewrite(body, Block.STATEMENTS_PROPERTY);
        bodyListRewrite.insertLast(inputStatement, null);
        bodyListRewrite.insertLast(expectedStatement, null);
        constructor.setBody(body);

        // コンストラクタをクラスに追加
        addMethod(constructor);
    }

    protected ExpressionStatement createConstructorBlockAssignment(String var) {
        // "this.var = var;" を生成
        Assignment assignment = ast.newAssignment();
        assignment.setOperator(Assignment.Operator.ASSIGN);
        FieldAccess leftOperand = ast.newFieldAccess();
        leftOperand.setExpression(ast.newThisExpression());
        leftOperand.setName(ast.newSimpleName(var));
        assignment.setLeftHandSide(leftOperand);
        assignment.setRightHandSide(ast.newSimpleName(var));
        ExpressionStatement ret = ast.newExpressionStatement(assignment);
        return ret;
    }

    protected TypeDeclaration getTargetType() {
        TypeDeclaration target = null;
        for (AbstractTypeDeclaration abstType : (List<AbstractTypeDeclaration>) getCompilationUnit().types()) {
            if (abstType instanceof TypeDeclaration) {
                TypeDeclaration type = (TypeDeclaration) abstType;
                if (type.getName().getIdentifier().equals(testSuite.getTestClassName())) {
                    target = type;
                }
            }
        }
        return target;
    }


    protected void addMethod(MethodDeclaration method) {
        TypeDeclaration modified = getTargetType();
        ListRewrite listRewrite = rewrite.getListRewrite(modified, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        listRewrite.insertLast(method, null);
    }

    protected void createDataMethod(MethodDeclaration origin) {
        MethodDeclaration method = ast.newMethodDeclaration();
        method.setConstructor(false);
        // メソッド名を設定
        method.setName(ast.newSimpleName(DATA_METHOD));
        // 修飾子を設定
        ListRewrite modifiersListRewrite = rewrite.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
        modifiersListRewrite.insertLast(ASTUtils.getParametersAnnotation(ast), null);
        modifiersListRewrite.insertLast(ASTUtils.getPublicModifier(ast), null);
        modifiersListRewrite.insertLast(ASTUtils.getStaticModifier(ast), null);
        // returnの型(Collection<Object[]>)を設定
        ParameterizedType type = ast.newParameterizedType(ast.newSimpleType(ast.newName("Collection")));
        ArrayType objectArray = ast.newArrayType(ast.newSimpleType(ast.newName("Object")));
        type.typeArguments().add(objectArray);
        method.setReturnType2(type);
        // bodyを設定
        method.setBody(_createDataBody(origin));

        // dataメソッドを追加
        TypeDeclaration modified = getTargetType();
        ListRewrite listRewrite = rewrite.getListRewrite(modified, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        listRewrite.insertLast(method, null);
        return;
    }

    protected Block _createDataBody(MethodDeclaration origin) {
        // return Arrays.asList(new Object[][] {{ input1, expected1 }, { input2, expected2 }}); を作成する
        // まず, new Object[][] {{ input1, expected1 }, { input2, expected2 }} を作成
        ArrayType arrayType = ast.newArrayType(ast.newSimpleType(ast.newName("Object")));
        arrayType.dimensions().add(ast.newDimension());
        ArrayCreation arrayCreation = ast.newArrayCreation();
        arrayCreation.setType(arrayType);
        // {{ input1, expected1 }, { input2, expected2 }} を設定
        arrayCreation.setInitializer(createInputAndExpected(origin));
        // Arrays.asListを作成
        MethodInvocation content = ast.newMethodInvocation();
        content.setExpression(ast.newSimpleName("Arrays"));
        content.setName(ast.newSimpleName("asList"));
        content.arguments().add(arrayCreation);
        // return文を作成
        ReturnStatement returnStatement = ast.newReturnStatement();
        returnStatement.setExpression(content);
        Block block = ast.newBlock();
        block.statements().add(returnStatement);
        return block;
    }

    protected ArrayInitializer createInputAndExpected(MethodDeclaration origin) {
        // {{ input1, expected1 }, { input2, expected2 }} を作る

        List<MethodDeclaration> similarMethods = new ArrayList<>();
        List<ASTNode> mostDifferentNodes = null;
        // 共通部分が一番少ないメソッドを探す && similarなメソッドを集める
        for (TestCase testCase : testSuite.getTestCases()) {
            MethodDeclaration method = testCase.getMethodDeclaration();
            if (similarAST(origin, method)) {
                similarMethods.add(method);
                if (mostDifferentNodes == null) {
                    mostDifferentNodes = ASTUtils.getDifferentNodes(method, origin);
                } else if (mostDifferentNodes.size() < ASTUtils.getDifferentNodes(origin, method).size()) {
                    mostDifferentNodes = ASTUtils.getDifferentNodes(method, origin);
                }
            }
        }

        // originのnodeをinputとexpectedに分ける
        List<ASTNode> inputRelatedNodes = new ArrayList<>();
        List<ASTNode> expectedRelatedNodes = new ArrayList<>();
        for (ASTNode node : mostDifferentNodes) {
            if (isInExpectedDeclaringNode(origin, node)) {
                expectedRelatedNodes.add(node);
            } else {
                inputRelatedNodes.add(node);
            }
        }
        // 各テストメソッドからinputとexpectedを抜き出して追加する
        ArrayInitializer arrayInitializer = ast.newArrayInitializer();
        ListRewrite listRewrite = rewrite.getListRewrite(arrayInitializer, ArrayInitializer.EXPRESSIONS_PROPERTY);

        List<ASTNode> originNodes = ASTUtils.getAllNodes(origin);
        for (MethodDeclaration similarMethod : similarMethods) {
            List<ASTNode> methodNodes = ASTUtils.getAllNodes(similarMethod);
            List<ASTNode> inputs = new ArrayList<>();
            List<ASTNode> expecteds = new ArrayList<>();
            for (int i = 0; i < originNodes.size(); i++) {
                ASTNode originNode = originNodes.get(i);
                if (expectedRelatedNodes.contains(originNode)) {
                    expecteds.add(methodNodes.get(i));
                } else if (inputRelatedNodes.contains(originNode)) {
                    inputs.add(methodNodes.get(i));
                }
            }
            ArrayInitializer inputAndExpected = ast.newArrayInitializer();
            ListRewrite dataListRewrite = rewrite.getListRewrite(inputAndExpected, ArrayInitializer.EXPRESSIONS_PROPERTY);
            // 抜き出したinputが1つならそのまま，複数なら{}に入れて追加
            if (inputs.size() == 1) {
                dataListRewrite.insertLast(inputs.get(0), null);
            } else {
                ArrayInitializer inputArray = ast.newArrayInitializer();
                ListRewrite inputListRewrite = rewrite.getListRewrite(inputArray, ArrayInitializer.EXPRESSIONS_PROPERTY);
                for (ASTNode input : inputs) {
                    inputListRewrite.insertLast(input, null);
                }
                dataListRewrite.insertLast(inputArray, null);
            }
            // 抜き出したexpectが1つならそのまま，複数なら{}に入れて追加
            if (expecteds.size() == 1) {
                dataListRewrite.insertLast(expecteds.get(0), null);
            } else {
                ArrayInitializer expectedArray = ast.newArrayInitializer();
                ListRewrite expectedListRewrite = rewrite.getListRewrite(expectedArray, ArrayInitializer.EXPRESSIONS_PROPERTY);
                for (ASTNode expected : expecteds) {
                    expectedListRewrite.insertLast(expected, null);
                }
                dataListRewrite.insertLast(expectedArray, null);
            }
            listRewrite.insertLast(inputAndExpected, null);
        }
        return arrayInitializer;
    }

    protected void modifyTestMethod(MethodDeclaration origin) {
        // 名前を変更
        origin.setName(ast.newSimpleName(METHOD_NAME));
        // 中身を追加
        _modifyTestMethod(origin);
    }

    protected void _modifyTestMethod(MethodDeclaration origin) {
        List<ASTNode> mostDifferentNodes = null;
        // 共通部分が一番少ないメソッドを探す
        for (TestCase testCase : testSuite.getTestCases()) {
            MethodDeclaration method = testCase.getMethodDeclaration();
            if (method.equals(origin)) {
                continue;
            }
            if (similarAST(origin, method)) {
                if (mostDifferentNodes == null) {
                    mostDifferentNodes = ASTUtils.getDifferentNodes(method, origin);
                } else if (mostDifferentNodes.size() < ASTUtils.getDifferentNodes(origin, method).size()) {
                    mostDifferentNodes = ASTUtils.getDifferentNodes(method, origin);
                }
            }
        }
        // commonじゃないnodeをinputとexpectedに変更する
        // commonsじゃないnodeをinputとexpectedに分ける
        List<ASTNode> inputRelatedNodes = new ArrayList<>();
        List<ASTNode> expectedRelatedNodes = new ArrayList<>();
        for (ASTNode node : mostDifferentNodes) {
            if (isInExpectedDeclaringNode(origin, node)) {
                expectedRelatedNodes.add(node);
            } else {
                inputRelatedNodes.add(node);
            }
        }
        // expectedを置換する
        if (expectedRelatedNodes.size() == 1) {
            ASTNode target = expectedRelatedNodes.get(0);
            SimpleName replace = ast.newSimpleName(EXPECTED_VAR);
            rewrite.replace(target, replace, null);
        } else {
            for (int i = 0; i < expectedRelatedNodes.size(); i++) {
                ASTNode target = expectedRelatedNodes.get(i);
                ArrayAccess replace = ast.newArrayAccess();
                replace.setArray(ast.newSimpleName(EXPECTED_VAR));
                replace.setIndex(ast.newNumberLiteral(String.valueOf(i)));
                rewrite.replace(target, replace, null);
            }
        }
        // inputを置換する
        if (inputRelatedNodes.size() == 1) {
            ASTNode target = inputRelatedNodes.get(0);
            SimpleName replace = ast.newSimpleName(INPUT_VAR);
            rewrite.replace(target, replace, null);
        } else {
            for (int i = 0; i < inputRelatedNodes.size(); i++) {
                ASTNode target = inputRelatedNodes.get(i);
                ArrayAccess replace = ast.newArrayAccess();
                replace.setArray(ast.newSimpleName(INPUT_VAR));
                replace.setIndex(ast.newNumberLiteral(String.valueOf(i)));
                rewrite.replace(target, replace, null);
            }
        }

        return;
    }

    protected Type getInputType(MethodDeclaration origin) {
        List<ASTNode> mostDifferentNodes = null;
        // 共通部分が一番少ないメソッドを探す
        for (TestCase testCase : testSuite.getTestCases()) {
            MethodDeclaration method = testCase.getMethodDeclaration();
            if (method.equals(origin)) {
                continue;
            }
            if (similarAST(origin, method)) {
                if (mostDifferentNodes == null) {
                    mostDifferentNodes = ASTUtils.getDifferentNodes(method, origin);
                } else if (mostDifferentNodes.size() < ASTUtils.getDifferentNodes(origin, method).size()) {
                    mostDifferentNodes = ASTUtils.getDifferentNodes(method, origin);
                }
            }
        }
        // commonじゃないnodeからinputを抽出する
        List<ASTNode> inputRelatedNodes = new ArrayList<>();
        for (ASTNode node : mostDifferentNodes) {
            if (!isInExpectedDeclaringNode(origin, node)) {
                inputRelatedNodes.add(node);
            }
        }
        Type ret;
        if (1 < inputRelatedNodes.size()) {
            ret = ast.newArrayType(ast.newSimpleType(ast.newName("Object")));
            if (ASTUtils.allStringLiteral(inputRelatedNodes)) {
                ret = ast.newArrayType(ast.newSimpleType(ast.newName("String")));
            } else if (ASTUtils.allNumberLiteral(inputRelatedNodes)) {
                ret = ast.newArrayType(ast.newPrimitiveType(PrimitiveType.DOUBLE));
            } else if (ASTUtils.allCharacterLiteral(inputRelatedNodes)) {
                ret = ast.newArrayType(ast.newPrimitiveType(PrimitiveType.CHAR));
            } else if (ASTUtils.allBooleanLiteral(inputRelatedNodes)) {
                ret = ast.newArrayType(ast.newPrimitiveType(PrimitiveType.BOOLEAN));
            }
        } else {
            ret = ast.newSimpleType(ast.newName("Object"));
            if (ASTUtils.allStringLiteral(inputRelatedNodes)) {
                ret = ast.newSimpleType(ast.newName("String"));
            } else if (ASTUtils.allNumberLiteral(inputRelatedNodes)) {
                ret = ast.newPrimitiveType(PrimitiveType.DOUBLE);
            } else if (ASTUtils.allCharacterLiteral(inputRelatedNodes)) {
                ret = ast.newPrimitiveType(PrimitiveType.CHAR);
            } else if (ASTUtils.allBooleanLiteral(inputRelatedNodes)) {
                ret = ast.newPrimitiveType(PrimitiveType.BOOLEAN);
            }
        }
        return ret;
    }

    public Type getExpectedType(MethodDeclaration origin) {
        List<ASTNode> mostDifferentNodes = null;
        // 共通部分が一番少ないメソッドを探す
        for (TestCase testCase : testSuite.getTestCases()) {
            MethodDeclaration method = testCase.getMethodDeclaration();
            if (method.equals(origin)) {
                continue;
            }
            if (similarAST(origin, method)) {
                if (mostDifferentNodes == null) {
                    mostDifferentNodes = ASTUtils.getDifferentNodes(method, origin);
                } else if (mostDifferentNodes.size() < ASTUtils.getDifferentNodes(origin, method).size()) {
                    mostDifferentNodes = ASTUtils.getDifferentNodes(method, origin);
                }
            }
        }
        // commonじゃないnodeからinputを抽出する
        List<ASTNode> expectedRelatedNodes = new ArrayList<>();
        for (ASTNode node : mostDifferentNodes) {
            if (isInExpectedDeclaringNode(origin, node)) {
                expectedRelatedNodes.add(node);
            }
        }
        Type ret;
        if (1 < expectedRelatedNodes.size()) {
            ret = ast.newArrayType(ast.newSimpleType(ast.newName("Object")));
            if (ASTUtils.allStringLiteral(expectedRelatedNodes)) {
                ret = ast.newArrayType(ast.newSimpleType(ast.newName("String")));
            } else if (ASTUtils.allNumberLiteral(expectedRelatedNodes)) {
                ret = ast.newArrayType(ast.newPrimitiveType(PrimitiveType.DOUBLE));
            } else if (ASTUtils.allCharacterLiteral(expectedRelatedNodes)) {
                ret = ast.newArrayType(ast.newPrimitiveType(PrimitiveType.CHAR));
            } else if (ASTUtils.allBooleanLiteral(expectedRelatedNodes)) {
                ret = ast.newArrayType(ast.newPrimitiveType(PrimitiveType.BOOLEAN));
            }
        } else {
            ret = ast.newSimpleType(ast.newName("Object"));
            if (ASTUtils.allStringLiteral(expectedRelatedNodes)) {
                ret = ast.newSimpleType(ast.newName("String"));
            } else if (ASTUtils.allNumberLiteral(expectedRelatedNodes)) {
                ret = ast.newPrimitiveType(PrimitiveType.DOUBLE);
            } else if (ASTUtils.allCharacterLiteral(expectedRelatedNodes)) {
                ret = ast.newPrimitiveType(PrimitiveType.CHAR);
            } else if (ASTUtils.allBooleanLiteral(expectedRelatedNodes)) {
                ret = ast.newPrimitiveType(PrimitiveType.BOOLEAN);
            }
        }
        return ret;
    }


    private boolean isInExpectedDeclaringNode(MethodDeclaration origin, ASTNode node) {
        List<MethodInvocation> assertions = ASTUtils.getAllAssertions(origin);
        for (MethodInvocation assertion : assertions) {
            Name expected;
            ASTNode expectedDeclaringNode = null;
            if (assertion.arguments().size() == 2) {
                expected = (Name) assertion.arguments().get(0);
                expectedDeclaringNode = getCompilationUnit().findDeclaringNode(expected.resolveBinding());
            } else if (assertion.arguments().size() == 3) {
                expected = (Name) assertion.arguments().get(1);
                expectedDeclaringNode = getCompilationUnit().findDeclaringNode(expected.resolveBinding());
            }
            while (node != null) {
                if (node.equals(expectedDeclaringNode)) {
                    return true;
                }
                node = node.getParent();
            }
        }
        return false;
    }

    protected void deleteExistingFieldDeclarations() {
        TypeDeclaration modified = getTargetType();
        ListRewrite listRewrite = rewrite.getListRewrite(modified, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        for (FieldDeclaration fieldDeclaration : modified.getFields()) {
            listRewrite.remove(fieldDeclaration, null);
        }
    }

    protected void deleteExistingMethodDeclarations(MethodDeclaration origin) {
        TypeDeclaration modified = getTargetType();
        ListRewrite listRewrite = rewrite.getListRewrite(modified, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        for (MethodDeclaration methodDeclaration : modified.getMethods()) {
            if (methodDeclaration.equals(origin)) {
                continue;
            }
            if (methodDeclaration.isConstructor() || methodDeclaration.getName().toString().startsWith("test")) {
                System.out.println("Remove " + methodDeclaration.getName());
                listRewrite.remove(methodDeclaration, null);
            }
        }
    }
}
