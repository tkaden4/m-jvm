package io.github.m

import io.github.m.asm.*

@Suppress("MemberVisibilityCanBePrivate")
@UseExperimental(ExperimentalUnsignedTypes::class)
object Generator {
    val internals: kotlin.collections.List<Pair> = listOf<java.lang.Class<*>>(
            Value.Definitions::class.java,
            Bool.Definitions::class.java,
            List.Definitions::class.java,
            Int.Definitions::class.java,
            Nat.Definitions::class.java,
            Real.Definitions::class.java,
            Char.Definitions::class.java,
            Symbol.Definitions::class.java,
            Data.Definitions::class.java,
            Process.Definitions::class.java,
            File.Definitions::class.java,
            Undefined.Definitions::class.java,
            Runtime.Definitions::class.java,
            Generator.Definitions::class.java,
            Expr.Definitions::class.java,
            Pair.Definitions::class.java,
            Variable.Definitions::class.java
    ).flatMap {
        it
                .fields
                .asSequence()
                .filter { field -> field.isAnnotationPresent(MField::class.java) }
                .map { field ->
                    val name = field.getAnnotation(MField::class.java).name.m
                    val variable = Variable.Global(field.name.m, it.name.m)
                    Pair(name, variable)
                }
                .toList()
    }

    fun closures(expr: Expr, env: Env): Set<List> = when (expr) {
        is Expr.Identifier -> when (env.vars[expr.name]) {
            null -> emptySet()
            is Variable.Global -> emptySet()
            is Variable.Local -> setOf(expr.name)
        }
        is Expr.List -> expr.exprs.flatMap { closures(it.cast(), env) }.toSet()
    }

    fun generateIdentifierExpr(name: List, env: Env) =
            GenerateResult(
                    when (val variable = env.vars[name]) {
                        null -> reflectiveVariableOperation(name.string, env.path.asType)
                        is Variable.Local -> localVariableOperation(variable.name.string, variable.index.value.toInt())
                        is Variable.Global -> globalVariableOperation(variable.name.string, variable.path.asType)
                    },
                    Declaration.empty,
                    env
            )

    fun generateNil(env: Env) = GenerateResult(nilOperation, Declaration.empty, env)

    fun generateIfExpr(cond: Expr, `true`: Expr, `false`: Expr, env: Env): GenerateResult = run {
        val condResult = generateExpr(cond, env)
        val trueResult = generateExpr(`true`, condResult.env)
        val falseResult = generateExpr(`false`, trueResult.env)
        GenerateResult(
                ifOperation(condResult.operation, trueResult.operation, falseResult.operation),
                block(condResult.declaration, trueResult.declaration, falseResult.declaration),
                falseResult.env
        )
    }

    fun generateLambdaExpr(name: List, expr: Expr, env: Env): GenerateResult = run {
        val methodName = Definitions.mangleLambdaName.asFunction(env.def, env.index).asList
        val env2 = env.copy(index = env.index.add(Nat(1.toUInt())))
        val closures = closures(expr, env).toList()
        val closureOperations = closures.map { generateIdentifierExpr(it, env2).operation }
        val (_, locals) = closures.plus(element = name).fold(0 to env.vars) { (index, map), name ->
            index + 1 to map + (name to Variable.Local(name, Nat(index.toUInt())))
        }
        val exprResult = generateExpr(expr, env2.copy(vars = locals, def = methodName))
        GenerateResult(
                lambdaOperation(env2.path.asType, methodName.string, closureOperations),
                block(exprResult.declaration, lambdaDeclaration(methodName.string, closures.map { it.string }, exprResult.operation)),
                env2
        )
    }

    fun generateDefExpr(name: List, expr: Expr, env: Env): GenerateResult = run {
        val env2 = env.copy(vars = env.vars + (name to Variable.Global(name, env.path)))
        val localEnv = env2.copy(def = name)
        val exprResult = generateExpr(expr, localEnv)
        if (env.vars[name] == null) {
            GenerateResult(
                    defOperation(name.asString, exprResult.operation, localEnv.path.asType),
                    block(exprResult.declaration, defDeclaration(name.asString, env.path.asType)),
                    env2
            )
        } else {
            GenerateResult(
                    generateIdentifierExpr(name, env).operation,
                    Declaration.empty,
                    env
            )
        }
    }

    fun generateSymbolExpr(name: List, env: Env): GenerateResult =
            GenerateResult(symbolOperation(name.asString), Declaration.empty, env)

    tailrec fun generateApplyExpr(fn: Expr, args: List, env: Env): GenerateResult = when (args) {
        is List.Nil -> generateApplyExpr(fn, List.Cons(Expr.List(List.Nil, fn.line), List.Nil), env)
        is List.Cons -> when (args.cdr) {
            is List.Cons -> generateApplyExpr(Expr.List(List.Cons(fn, List.Cons(args.car, List.Nil)), fn.line), args.cdr, env)
            is List.Nil -> {
                val fnResult = generateExpr(fn, env)
                val argResult = generateExpr(args.car.cast(), fnResult.env)
                GenerateResult(
                        applyOperation(fnResult.operation, argResult.operation),
                        block(fnResult.declaration, argResult.declaration),
                        argResult.env
                )
            }
        }
    }

    fun generateListExpr(expr: Expr.List, env: Env): GenerateResult = when (val exprs = expr.exprs) {
        is List.Nil -> generateNil(env)
        is List.Cons -> when ((exprs.car as? Expr.Identifier)?.name?.asString) {
            "if" -> generateIfExpr(exprs.cadr.cast(), exprs.caddr.cast(), exprs.cadddr.cast(), env)
            "lambda" -> generateLambdaExpr(exprs.cadr.cast<Expr.Identifier>().name, exprs.caddr.cast(), env)
            "def" -> generateDefExpr(exprs.cadr.cast<Expr.Identifier>().name, exprs.caddr.cast(), env)
            "symbol" -> generateSymbolExpr(exprs.cadr.cast<Expr.Identifier>().name, env)
            else -> generateApplyExpr(exprs.car.cast(), exprs.cdr.cast(), env)
        }
    }

    fun generateExpr(expr: Expr, env: Env): GenerateResult = try {
        when (expr) {
            is Expr.Identifier -> generateIdentifierExpr(expr.name, env)
            is Expr.List -> generateListExpr(expr, env)
        }.run { copy(operation = block(lineNumber(expr.line.value.toInt()), operation)) }
    } catch (e: java.lang.Error) {
        throw Error.Internal(e.message + "\n    at line ${expr.line}", e)
    }

    fun generateExprs(exprs: List, env: Env): GenerateResult = when (exprs) {
        List.Nil -> GenerateResult(Operation.empty, Declaration.empty, env)
        is List.Cons -> {
            val generateResultCar = generateExpr(exprs.car.cast(), env)
            val generateResultCdr = generateExprs(exprs.cdr, generateResultCar.env)
            GenerateResult(
                    block(generateResultCar.operation, pop, generateResultCdr.operation),
                    block(generateResultCar.declaration, generateResultCdr.declaration),
                    generateResultCdr.env
            )
        }
    }

    fun generate(name: List, out: File, exprs: List) = run {
        val internals = internals.map { it.first.asList to it.second.cast<Variable>() }.toMap()
        val env = Env(internals, name, List.Nil, Nat(0.toUInt()))
        val result = generateExprs(exprs.asList, env)
        Definitions.generateProgram.asFunction(name, out, result.operation, result.declaration)
    }

    @Suppress("unused")
    object Definitions {
        @MField("local-variable-operation")
        @JvmField
        val localVariableOperation: Value = Function { name, index ->
            localVariableOperation(name.asString, index.asNat.value.toInt())
        }

        @MField("global-variable-operation")
        @JvmField
        val globalVariableOperation: Value = Function { name, path ->
            globalVariableOperation(name.asString, path.asType)
        }

        @MField("reflective-variable-operation")
        @JvmField
        val reflectiveVariableOperation: Value = Function { name, path ->
            reflectiveVariableOperation(name.asString, path.asType)
        }

        @MField("if-operation")
        @JvmField
        val ifOperation: Value = Function { cond, `true`, `false` ->
            ifOperation(cond.asOperation, `true`.asOperation, `false`.asOperation)
        }

        @MField("def-operation")
        @JvmField
        val defOperation: Value = Function { name, operation, file ->
            defOperation(name.asString, operation.asOperation, file.asType)
        }

        @MField("def-declaration")
        @JvmField
        val defDeclaration: Value = Function { name, path ->
            defDeclaration(name.asString, path.asType)
        }

        @MField("lambda-operation")
        @JvmField
        val lambdaOperation: Value = Function { path, name, closures ->
            lambdaOperation(path.asType, name.asString, closures.asList.map { it as Operation })
        }

        @MField("lambda-declaration")
        @JvmField
        val lambdaDeclaration: Value = Function { name, closures, operation ->
            lambdaDeclaration(name.asString, closures.asList.map(Value::asString), operation.asOperation)
        }

        @MField("symbol-operation")
        @JvmField
        val symbolOperation: Value = Function { name ->
            symbolOperation(name.asString)
        }

        @MField("import-operation")
        @JvmField
        val importOperation: Value = Function { name ->
            importOperation(name.asString)
        }

        @MField("import-declaration")
        @JvmField
        val importDeclaration: Value = Function @Suppress("RedundantLambdaArrow") { _ ->
            Declaration.empty
        }

        @MField("apply-operation")
        @JvmField
        val applyOperation: Value = Function { fn, arg ->
            applyOperation(fn.asOperation, arg.asOperation)
        }

        @MField("nil-operation")
        @JvmField
        val nilOperation: Value = io.github.m.asm.nilOperation

        @MField("no-operation")
        @JvmField
        val noOperation: Value = Operation.empty

        @MField("no-declaration")
        @JvmField
        val noDeclaration: Value = Declaration.empty

        @MField("combine-operation")
        @JvmField
        val combineOperation: Value = Function { operation1, operation2 ->
            block(operation1.asOperation, operation2.asOperation)
        }

        @MField("ignore-result-operation")
        @JvmField
        val ignoreResultOperation: Value = Function { operation ->
            block(operation.asOperation, pop)
        }

        @MField("line-number-operation")
        @JvmField
        val lineNumberOperation: Value = Function { operation, line ->
            block(lineNumber(line.asNat.value.toInt()), operation.asOperation)
        }

        @MField("combine-declaration")
        @JvmField
        val combineDeclaration: Value = Function { declaration1, declaration2 ->
            block(declaration1.asDeclaration, declaration2.asDeclaration)
        }

        @MField("mangle-lambda-name")
        @JvmField
        val mangleLambdaName: Value = Function { name, index ->
            "${name.asString}_${index.asNat}".m
        }

        @MField("internal-variables")
        @JvmField
        val internalVariables: Value = List.valueOf(internals.asSequence())

        @MField("generate-program")
        @JvmField
        val generateProgram: Value = Function { name, out, operation, declaration ->
            val clazzName = QualifiedName(listOf(name.asString))
            val clazz = mainClass(Type.clazz(clazzName), operation.asOperation, declaration.asDeclaration)
            Process {
                clazz.generate(out.asFile.file)
                List.Nil
            }
        }

        @MField("debug")
        @JvmField
        val debug: Value = Function { x ->
            println(x)
            x
        }
    }
}