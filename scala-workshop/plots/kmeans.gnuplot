set terminal epslatex color size 8.1cm,5.2cm linewidth 1
set output 'kmeans.tex'

# Too broad: the graph itself stops at margins.
# set lmargin at screen 0.13
# set rmargin at screen 0.97

set key inside top right box

set style line 1 lt 1 lc rgb "blue"  lw 2
set style line 2 lt 2 lc rgb "red"   lw 2
set style line 3 lt 3 lc rgb "green" lw 2

set xlabel "Number of workers"
set ylabel "Iterations/s/worker" offset 2

set grid

# For 16M
# plot "kmeans.dat" using 1:(($2+$3+$4)/(3*$1)):xtic(1) ls 1 title '\small{APGAS}' with lp,\
#      "kmeans.dat" using 1:(($8+$9+$10)/(3*$1)) ls 2 title '\small{Akka}' with lp

# For 32M
plot "kmeans32.dat" using 1:(($2+$3+$4)/(3*$1)):xtic(1) ls 1 title '\small{APGAS}' with lp,\
     "kmeans32.dat" using 1:(($5+$6+$7)/(3*$1)) ls 2 title '\small{Akka}' with lp

