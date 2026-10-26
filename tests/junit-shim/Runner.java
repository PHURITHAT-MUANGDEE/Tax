import java.lang.reflect.*;
public class Runner { public static void main(String[] a) throws Exception {
  int ok = 0, bad = 0; Class<?> c = Class.forName(a[0]);
  for (Method m : c.getDeclaredMethods()) if (m.isAnnotationPresent(org.junit.jupiter.api.Test.class)) {
    Constructor<?> k = c.getDeclaredConstructor(); k.setAccessible(true); m.setAccessible(true);
    try { m.invoke(k.newInstance()); System.out.println("PASS " + m.getName()); ok++; }
    catch (InvocationTargetException e) { System.out.println("FAIL " + m.getName() + " :: " + e.getCause()); bad++; } }
  System.out.println("passed " + ok + " failed " + bad); System.exit(bad == 0 ? 0 : 1); } }
