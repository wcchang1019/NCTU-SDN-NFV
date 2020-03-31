from mininet.topo import Topo

class Project2_Topo_0516094( Topo ):
    def __init__( self ):
        Topo.__init__( self )

        # Add hosts
        h1 = self.addHost( 'h1' )
        h2 = self.addHost( 'h2' )

        # Add switches
        s1 = self.addSwitch( 's1' )
        s2 = self.addSwitch( 's2' )
        s3 = self.addSwitch( 's3' )

        # Add links
        self.addLink( h1, s1 )
        self.addLink( h2, s3 )
        self.addLink( s1, s2 )
        self.addLink( s1, s3 )
        self.addLink( s2, s3 )

topos = { 'topo_0516094' : Project2_Topo_0516094 }