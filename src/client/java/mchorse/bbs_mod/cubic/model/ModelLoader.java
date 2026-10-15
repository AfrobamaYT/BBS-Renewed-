package mchorse.bbs_mod.cubic.model;

import java.util.LinkedList;
import java.util.Queue;

public class ModelLoader implements Runnable
{
    private ModelManager manager;
    private Thread thread;
    private Queue<String> queue = new LinkedList<>();
    private boolean closed;

    public ModelLoader(ModelManager manager)
    {
        this.manager = manager;
    }

    public synchronized void add(String key)
    {
        if (this.closed) throw new IllegalStateException("Model loader is closed");
        this.queue.offer(key);

        if (this.thread == null)
        {
            this.thread = new Thread(this, "BBS model loader");
            this.thread.start();
        }
    }

    public synchronized void close()
    {
        this.closed = true;
        this.queue.clear();
    }

    @Override
    public void run()
    {
        while (true)
        {
            String model;
            synchronized (this)
            {
                model = this.closed ? null : this.queue.poll();
                if (model == null) { this.thread = null; return; }
            }

            try
            {
                this.manager.loadModel(model);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

    }
}
