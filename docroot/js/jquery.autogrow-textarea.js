(function(jQuery) {

    /*
     * Auto-growing textareas; technique ripped from Facebook
     */
    jQuery.fn.autogrow = function(options) {
        
        this.filter('textarea').each(function() {
            
            var $this       = jQuery(this),
                minHeight   = $this.height(),
                lineHeight  = $this.css('lineHeight');
            
            var shadow = jQuery('<div></div>').css({
                position:   'absolute',
                top:        -10000,
                left:       -10000,
                width:      jQuery(this).width(),
                fontSize:   $this.css('fontSize'),
                fontFamily: $this.css('fontFamily'),
                lineHeight: $this.css('lineHeight'),
                resize:     'none'
            }).appendTo(document.body);
            
            var update = function() {
                
                var val = this.value.replace(/</g, '&lt;')
                                    .replace(/>/g, '&gt;')
                                    .replace(/&/g, '&amp;')
                                    .replace(/\n/g, '<br/>');
                
                shadow.html(val);
                jQuery(this).css('height', Math.max(shadow.height() + 20, minHeight));
            }
            
            jQuery(this).change(update).keyup(update).keydown(update);
            
            update.apply(this);
            
        });
        
        return this;
        
    }
    
})(jQuery);