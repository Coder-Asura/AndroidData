package com.example.mvpdemo.base;

/**
 * Created by LiuLei on 2017/11/27.
 */
public interface Presenter<V> {
    void attachView(V mvpView);
    void detachView();
}
