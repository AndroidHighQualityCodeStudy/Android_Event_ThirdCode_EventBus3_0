/*
 * Copyright (C) 2012-2016 Markus Junginger, greenrobot (http://greenrobot.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.greenrobot.eventbus;

import org.greenrobot.eventbus.meta.SubscriberInfo;
import org.greenrobot.eventbus.meta.SubscriberInfoIndex;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class SubscriberMethodFinder {
    /*
     * In newer class files, compilers may add methods. Those are called bridge or synthetic methods.
     * EventBus must ignore both. There modifiers are not public but defined in the Java class file format:
     * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.6-200-A.1
     */
    private static final int BRIDGE = 0x40;
    private static final int SYNTHETIC = 0x1000;

    private static final int MODIFIERS_IGNORE = Modifier.ABSTRACT | Modifier.STATIC | BRIDGE | SYNTHETIC;

    // 以订阅者的Class为键,以订阅者中接收事件的方法列表为值
    // 分段锁ConcurrentHashMap,存在异步线程使用的情况吗? 这里有必要用分段锁吗????????????
    private static final Map<Class<?>, List<SubscriberMethod>> METHOD_CACHE = new ConcurrentHashMap<>();

    private List<SubscriberInfoIndex> subscriberInfoIndexes;
    // ????????????????????????
    private final boolean strictMethodVerification;
    //?????????????????????????
    private final boolean ignoreGeneratedIndex;
    // ????????????????????????
    private static final int POOL_SIZE = 4;
    // FindState的重用池
    private static final FindState[] FIND_STATE_POOL = new FindState[POOL_SIZE];

    SubscriberMethodFinder(List<SubscriberInfoIndex> subscriberInfoIndexes, boolean strictMethodVerification,
                           boolean ignoreGeneratedIndex) {
        this.subscriberInfoIndexes = subscriberInfoIndexes;
        this.strictMethodVerification = strictMethodVerification;
        this.ignoreGeneratedIndex = ignoreGeneratedIndex;
    }

    /**
     * 返回所有的注解方法
     *
     * @param subscriberClass
     * @return
     */
    List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {
        // ConcurrentHashMap 集合中有，则在集合中取
        List<SubscriberMethod> subscriberMethods = METHOD_CACHE.get(subscriberClass);
        if (subscriberMethods != null) {
            return subscriberMethods;
        }
        // 感觉像是忽略创建顺序的意思
        if (ignoreGeneratedIndex) {
            // 返回所有的注解方法
            subscriberMethods = findUsingReflection(subscriberClass);
        } else {
            subscriberMethods = findUsingInfo(subscriberClass);
        }
        // 若没有注册事subscribermesubscriberme件的接收方法，则抛出异常
        if (subscriberMethods.isEmpty()) {
            throw new EventBusException("Subscriber " + subscriberClass
                    + " and its super classes have no public methods with the @Subscribe annotation");
        } else {
            // 添加到 以订阅者的Class为键,以订阅者中接收事件的方法列表为值 的ConcurrentHashMap
            METHOD_CACHE.put(subscriberClass, subscriberMethods);
            //
            return subscriberMethods;
        }
    }

    /**
     * 返回订阅者的所有注解方法列表
     *
     * @param subscriberClass
     * @return
     */
    private List<SubscriberMethod> findUsingInfo(Class<?> subscriberClass) {
        // 返回一个缓存的FindState对象
        FindState findState = prepareFindState();
        findState.initForSubscriber(subscriberClass);
        while (findState.clazz != null) {
            // 这为空
            findState.subscriberInfo = getSubscriberInfo(findState);
            if (findState.subscriberInfo != null) {
                // 获取注解方法
                SubscriberMethod[] array = findState.subscriberInfo.getSubscriberMethods();
                for (SubscriberMethod subscriberMethod : array) {
                    // 检查什么?????????????
                    if (findState.checkAdd(subscriberMethod.method, subscriberMethod.eventType)) {
                        findState.subscriberMethods.add(subscriberMethod);
                    }
                }
            } else {
                findUsingReflectionInSingleClass(findState);
            }
            // 查找其父类,继续下一次循环
            findState.moveToSuperclass();
        }
        // 返回所有注解方法
        return getMethodsAndRelease(findState);
    }

    /**
     * 返回所有的方法
     *
     * @param findState
     * @return
     */
    private List<SubscriberMethod> getMethodsAndRelease(FindState findState) {
        // 订阅方法 方法中的参数类型 回调到的线程 优先级 粘贴模式
        List<SubscriberMethod> subscriberMethods = new ArrayList<>(findState.subscriberMethods);
        // 清空
        findState.recycle();
        // 重用FindState
        synchronized (FIND_STATE_POOL) {
            for (int i = 0; i < POOL_SIZE; i++) {
                if (FIND_STATE_POOL[i] == null) {
                    FIND_STATE_POOL[i] = findState;
                    break;
                }
            }
        }
        //
        return subscriberMethods;
    }

    /**
     * 锁定FIND_STATE_POOL
     * <p>
     * 从FIND_STATE_POOL中取一个不为空的FindState，并将FIND_STATE_POOL中对应项置空
     * 若FIND_STATE_POOL都为空，则创建一个FindState对象返回
     *
     * @return
     */
    private FindState prepareFindState() {
        // 锁定FIND_STATE_POOL
        synchronized (FIND_STATE_POOL) {
            // 循环清空FIND_STATE_POOL数组
            for (int i = 0; i < POOL_SIZE; i++) {
                FindState state = FIND_STATE_POOL[i];
                if (state != null) {
                    FIND_STATE_POOL[i] = null;
                    return state;
                }
            }
        }
        return new FindState();
    }

    private SubscriberInfo getSubscriberInfo(FindState findState) {
        if (findState.subscriberInfo != null && findState.subscriberInfo.getSuperSubscriberInfo() != null) {
            SubscriberInfo superclassInfo = findState.subscriberInfo.getSuperSubscriberInfo();
            if (findState.clazz == superclassInfo.getSubscriberClass()) {
                return superclassInfo;
            }
        }
        if (subscriberInfoIndexes != null) {
            for (SubscriberInfoIndex index : subscriberInfoIndexes) {
                SubscriberInfo info = index.getSubscriberInfo(findState.clazz);
                if (info != null) {
                    return info;
                }
            }
        }
        return null;
    }

    /**
     * 返回所有注解方法
     *
     * @param subscriberClass
     * @return
     */
    private List<SubscriberMethod> findUsingReflection(Class<?> subscriberClass) {
        // 从FIND_STATE_POOL中取一个不为空的FindState，并将FIND_STATE_POOL中对应项置空
        // 若FIND_STATE_POOL都为空，则创建一个FindState对象返回
        FindState findState = prepareFindState();
        // 赋值subscriberClass
        findState.initForSubscriber(subscriberClass);
        //
        while (findState.clazz != null) {
            // 找到有Subscribe注解，并且参数只有一个的方法
            findUsingReflectionInSingleClass(findState);
            // ????????
            findState.moveToSuperclass();
        }
        // 返回所有注解方法
        return getMethodsAndRelease(findState);
    }

    /**
     * 找到有Subscribe注解，并且参数只有一个的方法
     *
     * @param findState
     */
    private void findUsingReflectionInSingleClass(FindState findState) {
        Method[] methods;
        try {
            // 获取订阅者中的全部方法
            // This is faster than getMethods, especially when subscribers are fat classes like Activities
            methods = findState.clazz.getDeclaredMethods();
        } catch (Throwable th) {
            // Workaround for java.lang.NoClassDefFoundError, see https://github.com/greenrobot/EventBus/issues/149
            methods = findState.clazz.getMethods();
            findState.skipSuperClasses = true;
        }
        // 循环订阅者中的全部方法
        for (Method method : methods) {
            // 方法类型
            int modifiers = method.getModifiers();
            // 必须是公有方法 && 不能是abstract static bridge synthetic
            if ((modifiers & Modifier.PUBLIC) != 0 && (modifiers & MODIFIERS_IGNORE) == 0) {
                // 获取方法的全部参数类型
                Class<?>[] parameterTypes = method.getParameterTypes();
                // 参数为1个
                if (parameterTypes.length == 1) {
                    // 获取注解方法
                    Subscribe subscribeAnnotation = method.getAnnotation(Subscribe.class);
                    //
                    if (subscribeAnnotation != null) {
                        // 获取其对应的参数类型
                        Class<?> eventType = parameterTypes[0];
                        //
                        if (findState.checkAdd(method, eventType)) {
                            // 所在线程
                            ThreadMode threadMode = subscribeAnnotation.threadMode();
                            // 订阅方法 方法中的参数类型 回调到的线程 优先级 粘贴模式
                            findState.subscriberMethods.add(new SubscriberMethod(method, eventType, threadMode,
                                    subscribeAnnotation.priority(), subscribeAnnotation.sticky()));
                        }
                    }
                }
                // 抛出异常
                else if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
                    String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                    throw new EventBusException("@Subscribe method " + methodName +
                            "must have exactly 1 parameter but has " + parameterTypes.length);
                }
            }
            // 抛出异常
            else if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
                String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                throw new EventBusException(methodName +
                        " is a illegal @Subscribe method: must be public, non-static, and non-abstract");
            }
        }
    }

    static void clearCaches() {
        METHOD_CACHE.clear();
    }

    static class FindState {
        // 订阅方法 方法中的参数类型 回调到的线程 优先级 粘贴模式
        final List<SubscriberMethod> subscriberMethods = new ArrayList<>();
        // key 方法对应的参数类型  value:方法本身
        final Map<Class, Object> anyMethodByEventType = new HashMap<>();
        // key:"方法名 > 参数名"   value:方法对应的class
        final Map<String, Class> subscriberClassByMethodKey = new HashMap<>();
        final StringBuilder methodKeyBuilder = new StringBuilder(128);

        Class<?> subscriberClass;
        Class<?> clazz;
        boolean skipSuperClasses;
        SubscriberInfo subscriberInfo;

        void initForSubscriber(Class<?> subscriberClass) {
            this.subscriberClass = clazz = subscriberClass;
            skipSuperClasses = false;
            subscriberInfo = null;
        }

        void recycle() {
            // 清空methods列表
            subscriberMethods.clear();
            // 清空  key 方法对应的参数类型  value:方法本身
            anyMethodByEventType.clear();
            // 清空 key:"方法名 > 参数名"   value:方法对应的class
            subscriberClassByMethodKey.clear();
            // "方法名 > 参数名"
            methodKeyBuilder.setLength(0);
            subscriberClass = null;
            clazz = null;
            skipSuperClasses = false;
            subscriberInfo = null;
        }

        /**
         * @param method    方法
         * @param eventType 方法对应的参数类型
         * @return
         */
        boolean checkAdd(Method method, Class<?> eventType) {
            // 2 level check: 1st level with event type only (fast), 2nd level with complete signature when required.
            // Usually a subscriber doesn't have methods listening to the same event type.
            // 以方法对应的参数类型为key；以方法本身为值
            // 返回oldValue 或者 null
            Object existing = anyMethodByEventType.put(eventType, method);
            if (existing == null) {
                return true;
            } else {
                // oldValue
                if (existing instanceof Method) {
                    //
                    if (!checkAddWithMethodSignature((Method) existing, eventType)) {
                        // Paranoia check
                        throw new IllegalStateException();
                    }
                    // Put any non-Method object to "consume" the existing Method
                    anyMethodByEventType.put(eventType, this);
                }
                return checkAddWithMethodSignature(method, eventType);
            }
        }

        /**
         * ?????????????????????????????????
         *
         * @param method    方法
         * @param eventType 方法对应的参数类型
         * @return
         */
        private boolean checkAddWithMethodSignature(Method method, Class<?> eventType) {
            //
            methodKeyBuilder.setLength(0);
            // 方法名 > 参数名
            methodKeyBuilder.append(method.getName());
            methodKeyBuilder.append('>').append(eventType.getName());
            // 方法名 > 参数名
            String methodKey = methodKeyBuilder.toString();
            // 方法对应的class
            Class<?> methodClass = method.getDeclaringClass();
            // key:"方法名 > 参数名"  value:方法对应的class
            Class<?> methodClassOld = subscriberClassByMethodKey.put(methodKey, methodClass);
            if (methodClassOld == null || methodClassOld.isAssignableFrom(methodClass)) {
                // Only add if not already found in a sub class
                return true;
            } else {
                // Revert the put, old class is further down the class hierarchy
                subscriberClassByMethodKey.put(methodKey, methodClassOld);
                return false;
            }
        }

        void moveToSuperclass() {
            if (skipSuperClasses) {
                clazz = null;
            } else {
                clazz = clazz.getSuperclass();
                String clazzName = clazz.getName();
                /** Skip system classes, this just degrades performance. */
                if (clazzName.startsWith("java.") || clazzName.startsWith("javax.") || clazzName.startsWith("android.")) {
                    clazz = null;
                }
            }
        }
    }

}
