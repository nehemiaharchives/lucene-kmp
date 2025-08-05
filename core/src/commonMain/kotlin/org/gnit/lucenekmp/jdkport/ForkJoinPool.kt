package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.Job

@Ported(from = "ForkJoinPool")
class ForkJoinPool {

    /**
     * Interface for extending managed parallelism for tasks running
     * in [ForkJoinPool]s.
     *
     *
     * A `ManagedBlocker` provides two methods.  Method
     * [.isReleasable] must return `true` if blocking is
     * not necessary. Method [.block] blocks the current thread
     * if necessary (perhaps internally invoking `isReleasable`
     * before actually blocking). These actions are performed by any
     * thread invoking [ ][ForkJoinPool.managedBlock].  The unusual
     * methods in this API accommodate synchronizers that may, but
     * don't usually, block for long periods. Similarly, they allow
     * more efficient internal handling of cases in which additional
     * workers may be, but usually are not, needed to ensure
     * sufficient parallelism.  Toward this end, implementations of
     * method `isReleasable` must be amenable to repeated
     * invocation. Neither method is invoked after a prior invocation
     * of `isReleasable` or `block` returns `true`.
     *
     *
     * For example, here is a ManagedBlocker based on a
     * ReentrantLock:
     * <pre> `class ManagedLocker implements ManagedBlocker {
     * final ReentrantLock lock;
     * boolean hasLock = false;
     * ManagedLocker(ReentrantLock lock) { this.lock = lock; }
     * public boolean block() {
     * if (!hasLock)
     * lock.lock();
     * return true;
     * }
     * public boolean isReleasable() {
     * return hasLock || (hasLock = lock.tryLock());
     * }
     * }`</pre>
     *
     *
     * Here is a class that possibly blocks waiting for an
     * item on a given queue:
     * <pre> `class QueueTaker<E> implements ManagedBlocker {
     * final BlockingQueue<E> queue;
     * volatile E item = null;
     * QueueTaker(BlockingQueue<E> q) { this.queue = q; }
     * public boolean block() throws InterruptedException {
     * if (item == null)
     * item = queue.take();
     * return true;
     * }
     * public boolean isReleasable() {
     * return item != null || (item = queue.poll()) != null;
     * }
     * public E getItem() { // call after pool.managedBlock completes
     * return item;
     * }
     * }`</pre>
     */
    interface ManagedBlocker {
        /**
         * Possibly blocks the current thread, for example waiting for
         * a lock or condition.
         *
         * @return `true` if no additional blocking is necessary
         * (i.e., if isReleasable would return true)
         * @throws InterruptedException if interrupted while waiting
         * (the method is not required to do so, but is allowed to)
         */
        @Throws(InterruptedException::class)
        fun block(): Boolean

        /**
         * Returns `true` if blocking is unnecessary.
         * @return `true` if blocking is unnecessary
         */
        val isReleasable: Boolean
    }

    companion object{
        /**
         * Runs the given possibly blocking task.  When [ ][ForkJoinTask.inForkJoinPool], this
         * method possibly arranges for a spare thread to be activated if
         * necessary to ensure sufficient parallelism while the current
         * thread is blocked in [blocker.block()][ManagedBlocker.block].
         *
         *
         * This method repeatedly calls `blocker.isReleasable()` and
         * `blocker.block()` until either method returns `true`.
         * Every call to `blocker.block()` is preceded by a call to
         * `blocker.isReleasable()` that returned `false`.
         *
         *
         * If not running in a ForkJoinPool, this method is
         * behaviorally equivalent to
         * <pre> `while (!blocker.isReleasable())
         * if (blocker.block())
         * break;`</pre>
         *
         * If running in a ForkJoinPool, the pool may first be expanded to
         * ensure sufficient parallelism available during the call to
         * `blocker.block()`.
         *
         * @param blocker the blocker task
         * @throws InterruptedException if `blocker.block()` did so
         */
        @Throws(InterruptedException::class)
        fun managedBlock(blocker: ManagedBlocker) {
            val t: /*java.lang.Thread*/ Job
            val p: ForkJoinPool

            // TODO implement if needed
            /*if ((java.lang.Thread.currentThread().also { t = it }) is ForkJoinWorkerThread &&
                ((t as ForkJoinWorkerThread).pool.also { p = it }) != null
            ) p.compensatedBlock(blocker)

            else*/ unmanagedBlock(blocker)
        }

        /** ManagedBlock for external threads  */
        @Throws(InterruptedException::class)
        private fun unmanagedBlock(blocker: ManagedBlocker) {
            //java.util.Objects.requireNonNull<ManagedBlocker>(blocker)
            do {
            } while (!blocker.isReleasable && !blocker.block())
        }
    }

}