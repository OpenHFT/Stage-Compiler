package net.openhft.sg;

@Staged
public class ParametersFieldsNameCollision {

    int a = 0;

    public void foo(int a) {
        System.out.println(a);
    }
}
