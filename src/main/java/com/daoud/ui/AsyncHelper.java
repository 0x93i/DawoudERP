package com.daoud.ui;

import javafx.concurrent.Task;
import javafx.scene.Node;

import java.util.function.Consumer;

/**
 * مساعد بسيط لتشغيل استعلامات قاعدة البيانات (أو أي شغل بطيء) في thread منفصل
 * عن الواجهة، عشان البرنامج ميهنجش وهو بيجيب أو بيسجل بيانات من Supabase.
 *
 * الاستخدام:
 * <pre>
 *   AsyncHelper.run(
 *       () -> SupplierDAO.getAllSuppliers(),   // الشغل البطيء - يتنفذ في الخلفية
 *       result -> renderTable(result),         // لما ينجح - يتنفذ على واجهة المستخدم
 *       error -> DialogHelper... ,              // (اختياري) لو فشل
 *       saveBtn                                 // (اختياري) عناصر تتعطل أثناء التنفيذ
 *   );
 * </pre>
 */
public class AsyncHelper {

    /** شغل بيرجع نتيجة (زي DAO.getAll()) وممكن يرمي Exception عادي (مش لازم checked خاص). */
    public interface Work<T> {
        T call() throws Exception;
    }

    /** شغل من غير نتيجة (زي DAO.insert()) وممكن يرمي Exception عادي. */
    public interface VoidWork {
        void call() throws Exception;
    }

    // ── نسخة بترجع نتيجة ──

    public static <T> void run(Work<T> backgroundWork, Consumer<T> onSuccess, Node... disableWhileRunning) {
        run(backgroundWork, onSuccess, null, disableWhileRunning);
    }

    public static <T> void run(Work<T> backgroundWork, Consumer<T> onSuccess,
                               Consumer<Throwable> onError, Node... disableWhileRunning) {
        setDisabled(disableWhileRunning, true);

        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return backgroundWork.call();
            }
        };

        task.setOnSucceeded(e -> {
            setDisabled(disableWhileRunning, false);
            if (onSuccess != null) onSuccess.accept(task.getValue());
        });

        task.setOnFailed(e -> {
            setDisabled(disableWhileRunning, false);
            Throwable ex = task.getException();
            System.err.println("Async error: " + (ex != null ? ex.getMessage() : "unknown"));
            if (onError != null) onError.accept(ex);
        });

        Thread thread = new Thread(task, "daoud-async");
        thread.setDaemon(true);
        thread.start();
    }

    // ── نسخة من غير نتيجة (حفظ/حذف/تحديث) ──

    public static void runVoid(VoidWork backgroundWork, Runnable onSuccess, Node... disableWhileRunning) {
        runVoid(backgroundWork, onSuccess, null, disableWhileRunning);
    }

    public static void runVoid(VoidWork backgroundWork, Runnable onSuccess,
                               Consumer<Throwable> onError, Node... disableWhileRunning) {
        run(() -> { backgroundWork.call(); return null; },
                ignored -> { if (onSuccess != null) onSuccess.run(); },
                onError, disableWhileRunning);
    }

    private static void setDisabled(Node[] nodes, boolean disabled) {
        if (nodes == null) return;
        for (Node n : nodes) if (n != null) n.setDisable(disabled);
    }
}