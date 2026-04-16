package com.bcfinancial.servlet;

import java.io.*;

public interface CommandProcessor {
    public void init() throws Exception;
    public void process(ObjectInputStream in,ObjectOutputStream out) throws Exception;
}