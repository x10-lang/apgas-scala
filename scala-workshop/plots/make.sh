#!/usr/bin/env bash

gnuplot uts.gnuplot
epstopdf uts.eps
mv uts.{tex,eps,pdf} ../figures/

gnuplot kmeans.gnuplot
epstopdf kmeans.eps
mv kmeans.{tex,eps,pdf} ../figures/
