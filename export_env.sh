#!/bin/bash

export $(grep -vE '^\s*#' .env | grep -vE '^\s*$')
