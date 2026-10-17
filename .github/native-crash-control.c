/* Deliberate user-mode fault for the disposable Windows CI capture self-test. */
void entry(void)
{
    volatile int *target = (volatile int *)0;
    *target = 1;
}
