package streamproof;

import java.lang.reflect.*;
import java.util.*;

/** Cached SRG/MCP access without distributing Minecraft classes or sources. */
public final class GameAccess {
    private static final Map<String, Method> methods = new HashMap<>();
    private static final Map<String, Field> fields = new HashMap<>();
    private GameAccess() {}
    public static Object call(Object target, String names, Object... args) throws ReflectiveOperationException {
        Class<?> type = target instanceof Class ? (Class<?>)target : target.getClass();
        StringBuilder key = new StringBuilder(type.getName()).append('/').append(names);
        for (Object a : args) key.append('/').append(a == null ? "null" : a.getClass().getName());
        Method method = methods.get(key.toString());
        if (method == null) {
            search: for (String name : names.split("\\|")) {
                for (Class<?> c=type; c!=null; c=c.getSuperclass()) {
                    for (Method m : c.getDeclaredMethods()) {
                        if (!m.getName().equals(name) || m.getParameterTypes().length!=args.length) continue;
                        Class<?>[] parameters=m.getParameterTypes(); boolean matches=true;
                        for (int i=0;i<args.length;i++) {
                            Class<?> p=parameters[i];
                            if (p==int.class) p=Integer.class;
                            else if (p==float.class) p=Float.class;
                            else if (p==double.class) p=Double.class;
                            else if (p==boolean.class) p=Boolean.class;
                            if (args[i]!=null && !p.isInstance(args[i])) { matches=false; break; }
                        }
                        if (matches) { method=m; break search; }
                    }
                }
            }
            if (method==null) throw new NoSuchMethodException(key.toString());
            method.setAccessible(true); methods.put(key.toString(),method);
        }
        return method.invoke(target instanceof Class ? null : target,args);
    }
    public static Field field(Object target, String names) throws ReflectiveOperationException {
        Class<?> type=target instanceof Class ? (Class<?>)target : target.getClass();
        String key=type.getName()+"/"+names; Field field=fields.get(key);
        if (field==null) {
            search: for (String name:names.split("\\|")) for (Class<?> c=type;c!=null;c=c.getSuperclass()) {
                try { field=c.getDeclaredField(name); break search; } catch (NoSuchFieldException ignored) {}
            }
            if (field==null) throw new NoSuchFieldException(key);
            field.setAccessible(true); fields.put(key,field);
        }
        return field;
    }
    public static Object get(Object target,String names) throws ReflectiveOperationException {
        return field(target,names).get(target instanceof Class ? null : target);
    }
    public static double number(Object target,String names) throws ReflectiveOperationException {
        return ((Number)get(target,names)).doubleValue();
    }
    public static double value(Object target,String names,Object... args) throws ReflectiveOperationException {
        return ((Number)call(target,names,args)).doubleValue();
    }
}
