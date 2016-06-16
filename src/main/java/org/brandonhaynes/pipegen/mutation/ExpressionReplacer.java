package org.brandonhaynes.pipegen.mutation;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import javassist.expr.NewExpr;
import org.brandonhaynes.pipegen.PipeGen;
import org.brandonhaynes.pipegen.utilities.JarClassPath;
import org.brandonhaynes.pipegen.utilities.JarUpdater;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.logging.Logger;

public class ExpressionReplacer {
    private static final Logger log = Logger.getLogger(ExpressionReplacer.class.getName());

    public static void replaceExpression(URL jarLocation, String className, String methodName, int line,
                                         String targetExpression, String replacementExpression, Path backupPath)
            throws IOException, NotFoundException, CannotCompileException {
        ClassPool specificPool = new ClassPool(false);
        specificPool.appendSystemPath();
        specificPool.insertClassPath(new JarClassPath(jarLocation));

        replaceExpression(specificPool.get(className), methodName, line, targetExpression,
                          replacementExpression, backupPath);
    }

    public static void replaceExpression(String className, String methodName, int line,
                                         String targetExpression, String replacementExpression,
                                         ClassPool pool, Path backupPath)
            throws IOException, NotFoundException, CannotCompileException {
        replaceExpression(pool.get(className), methodName, line, targetExpression, replacementExpression, backupPath);
    }

    private static void replaceExpression(CtClass targetClass, String methodName,
                                          int line, String targetExpression, String replacementExpression,
                                          Path backupPath)
            throws IOException, NotFoundException, CannotCompileException {
        targetClass.defrost();

        if(isConstructor(methodName))
            replaceExpression(targetClass.getConstructors(), line, targetExpression, replacementExpression);
        else
            replaceExpression(targetClass.getDeclaredMethod(methodName), line, targetExpression, replacementExpression);

        if(targetClass.isModified())
            JarUpdater.replaceClass(targetClass.getClassPool().find(targetClass.getName()), targetClass, backupPath);
    }

    private static void replaceExpression(CtConstructor[] constructors, int line,
                                          String targetExpression, String replacementExpression)
            throws IOException, NotFoundException, CannotCompileException {
        for(CtConstructor constructor: constructors)
            replaceExpression(constructor, line, targetExpression, replacementExpression);
    }

    private static void replaceExpression(CtBehavior behavior, int line, String targetExpression, String replacementExpression)
            throws IOException, NotFoundException, CannotCompileException {
        behavior.instrument(new ExprEditor() {
            public void edit(NewExpr expression) throws CannotCompileException {
                if(expression.getLineNumber() == line &&
                   expression.getClassName().matches(targetExpression)) {
                    log.info("Modifying " + behavior.getLongName());
                    expression.replace(replacementExpression);
                }
            }

            public void edit(MethodCall expression) throws CannotCompileException {
                if(!behavior.getDeclaringClass().getName().startsWith(PipeGen.class.getPackage().getName()) &&
                        !expression.getClassName().startsWith(PipeGen.class.getPackage().getName()) &
                        expression.getLineNumber() == line &&
                        expression.getMethodName().matches(targetExpression)) {

                        log.info("Modifying " + behavior.getLongName() + ":" + expression.getSignature());
                        expression.replace(replacementExpression);
                }
            }
        });
    }

    private static boolean isConstructor(String name) {
        return name.equals("<init>");
    }
}