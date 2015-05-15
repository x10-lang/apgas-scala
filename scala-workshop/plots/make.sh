#!/usr/bin/env bash

gnuplot uts.gnuplot

epstopdf uts.eps

mv uts.{tex,eps,pdf} ../figures/
